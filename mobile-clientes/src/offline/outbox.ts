import { ApiError, isOfflineError } from '../api/client';
import type { OrderItemRequest, OrderResponse } from '../api/types';
import { recordSent, type OrderLine } from './orderHistory';
import { readJson, writeJson, type KeyValueStore } from './storage';

/**
 * Bandeja de salida de pedidos hechos sin conexión. Cada pedido se guarda en
 * el teléfono y se envía cuando vuelve la red.
 *
 * Para no mandar a cocina un pedido dos veces, solo se reintenta solo cuando
 * la petición no llegó al servidor (error de red). Si el servidor tardó
 * demasiado, el pedido pudo haberse registrado: queda como "sin confirmar" y
 * el comensal decide si reenviarlo después de confirmar con el mesero.
 */

export type PendingStatus = 'pending' | 'unconfirmed' | 'expired' | 'rejected';

export interface PendingOrder {
  localId: string;
  slug: string;
  tableNumber: number;
  items: OrderItemRequest[];
  /** Resumen legible para mostrar en la lista. */
  summary: string;
  /** Lo que pidió, para mostrarlo en el historial. Falta en pedidos de versiones anteriores. */
  lines?: OrderLine[];
  estimatedTotal: number;
  createdAt: string;
  status: PendingStatus;
  message: string | null;
}

export interface SentOrder {
  localId: string;
  order: OrderResponse;
}

export interface FlushResult {
  sent: SentOrder[];
  remaining: PendingOrder[];
}

const KEY = 'outbox';

export async function listPending(store: KeyValueStore): Promise<PendingOrder[]> {
  return (await readJson<PendingOrder[]>(store, KEY)) ?? [];
}

let queue: Promise<unknown> = Promise.resolve();

/**
 * Lee, modifica y guarda la bandeja sin que otra operación se cuele en medio.
 * Dentro nunca hay peticiones de red: el candado dura milisegundos.
 */
function mutate(store: KeyValueStore, change: (orders: PendingOrder[]) => PendingOrder[]): Promise<PendingOrder[]> {
  const run = queue.then(async () => {
    const next = change(await listPending(store));
    await writeJson(store, KEY, next);
    return next;
  });
  queue = run.catch(() => undefined);
  return run;
}

/** Aplica el resultado de un envío sobre la bandeja actual, que pudo cambiar mientras tanto. */
function applyOutcomes(store: KeyValueStore, sentIds: Set<string>, updated: Map<string, PendingOrder>): Promise<PendingOrder[]> {
  return mutate(store, (orders) =>
    orders.filter((order) => !sentIds.has(order.localId)).map((order) => updated.get(order.localId) ?? order),
  );
}

export async function enqueue(
  store: KeyValueStore,
  order: Omit<PendingOrder, 'localId' | 'createdAt' | 'status' | 'message'>,
  now: Date = new Date(),
): Promise<PendingOrder> {
  const pending: PendingOrder = {
    ...order,
    localId: `${now.getTime().toString(36)}-${Math.random().toString(36).slice(2, 8)}`,
    createdAt: now.toISOString(),
    status: 'pending',
    message: null,
  };
  await mutate(store, (orders) => [...orders, pending]);
  return pending;
}

export async function discard(store: KeyValueStore, localId: string): Promise<PendingOrder[]> {
  return mutate(store, (orders) => orders.filter((order) => order.localId !== localId));
}

type Sender = (order: PendingOrder) => Promise<OrderResponse>;

/**
 * Envía los pedidos en estado "pending" que están dentro de la ventana de
 * envío automático. Se detiene en el primer error de red.
 */
export async function flush(
  store: KeyValueStore,
  send: Sender,
  options: { now?: Date; autoSendWindowMs: number },
): Promise<FlushResult> {
  const now = options.now ?? new Date();
  const orders = await listPending(store);
  const sent: SentOrder[] = [];
  const updated = new Map<string, PendingOrder>();

  for (const order of orders) {
    if (order.status !== 'pending') continue;
    const age = now.getTime() - new Date(order.createdAt).getTime();
    if (age > options.autoSendWindowMs) {
      updated.set(order.localId, {
        ...order,
        status: 'expired',
        message: 'Lleva mucho tiempo guardado. Confirma que sigues en la mesa antes de enviarlo.',
      });
      continue;
    }
    const outcome = await attempt(store, order, send);
    if (outcome.kind === 'sent') {
      sent.push({ localId: order.localId, order: outcome.order });
    } else {
      updated.set(order.localId, outcome.order);
      // Si no hay conexión para uno, no la hay para los siguientes.
      if (outcome.kind === 'offline') break;
    }
  }

  const remaining = await applyOutcomes(store, new Set(sent.map((s) => s.localId)), updated);
  return { sent, remaining };
}

/**
 * Envío manual de un pedido concreto (botón "Enviar ahora"). Lo usa el
 * comensal para reenviar uno vencido, sin confirmar o rechazado.
 */
export async function sendOne(
  store: KeyValueStore,
  localId: string,
  send: Sender,
): Promise<{ sent: SentOrder | null; remaining: PendingOrder[] }> {
  const orders = await listPending(store);
  const target = orders.find((order) => order.localId === localId);
  if (!target) {
    return { sent: null, remaining: orders };
  }
  const outcome = await attempt(store, target, send);
  const remaining =
    outcome.kind === 'sent'
      ? await applyOutcomes(store, new Set([localId]), new Map())
      : await applyOutcomes(store, new Set(), new Map([[localId, outcome.order]]));
  return { sent: outcome.kind === 'sent' ? { localId, order: outcome.order } : null, remaining };
}

type Outcome =
  | { kind: 'sent'; order: OrderResponse }
  | { kind: 'offline'; order: PendingOrder }
  | { kind: 'kept'; order: PendingOrder };

async function attempt(store: KeyValueStore, order: PendingOrder, send: Sender): Promise<Outcome> {
  let response: OrderResponse;
  try {
    response = await send(order);
  } catch (error) {
    return failure(order, error);
  }
  // Se registra antes de sacarlo de la bandeja: si la app se cierra en medio,
  // el pedido queda en el historial aunque siga en la bandeja, nunca en ninguno.
  await recordSent(store, order, response);
  return { kind: 'sent', order: response };
}

function failure(order: PendingOrder, error: unknown): Outcome {
  if (isOfflineError(error)) {
    return { kind: 'offline', order: { ...order, status: 'pending', message: null } };
  }
  if (error instanceof ApiError && error.kind === 'timeout') {
    return {
      kind: 'kept',
      order: {
        ...order,
        status: 'unconfirmed',
        message: 'El servidor no respondió a tiempo. Pregunta al mesero si llegó antes de reenviarlo.',
      },
    };
  }
  if (error instanceof ApiError && error.kind === 'throttled') {
    return { kind: 'offline', order: { ...order, status: 'pending', message: error.message } };
  }
  const message = error instanceof Error ? error.message : 'No se pudo enviar el pedido.';
  return { kind: 'kept', order: { ...order, status: 'rejected', message } };
}
