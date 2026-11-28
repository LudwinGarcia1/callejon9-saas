import type { OrderResponse } from '../api/types';
import { readJson, writeJson, type KeyValueStore } from './storage';

/**
 * Pedidos que el comensal ya envió a cocina, guardados en el teléfono para que
 * pueda consultarlos sin conexión.
 *
 * Se guarda lo que pidió esta persona (sus líneas), no la orden completa de la
 * mesa: el backend acumula en una sola orden lo que pide toda la mesa.
 */

export interface OrderLine {
  name: string;
  quantity: number;
  unitPrice: number;
  notes: string;
}

export interface SentRecord {
  localId: string;
  folio: string;
  orderId: string;
  slug: string;
  tableNumber: number;
  /** Cuando se guardó el pedido (puede ser antes de enviarlo, si no había red). */
  createdAt: string;
  sentAt: string;
  lines: OrderLine[];
  estimatedTotal: number;
}

const KEY = 'history';
/** Solo interesa lo de la visita actual: lo de más de un día se descarta. */
export const HISTORY_TTL_MS = 24 * 60 * 60_000;
export const HISTORY_MAX = 30;

let queue: Promise<unknown> = Promise.resolve();

export async function listHistory(store: KeyValueStore, now: Date = new Date()): Promise<SentRecord[]> {
  const records = (await readJson<SentRecord[]>(store, KEY)) ?? [];
  return prune(records, now);
}

/** Pedidos de una mesa, del más reciente al más antiguo. */
export async function historyForTable(
  store: KeyValueStore,
  slug: string,
  tableNumber: number,
  now: Date = new Date(),
): Promise<SentRecord[]> {
  return (await listHistory(store, now))
    .filter((r) => r.slug === slug && r.tableNumber === tableNumber)
    .sort((a, b) => b.sentAt.localeCompare(a.sentAt));
}

export function recordSent(
  store: KeyValueStore,
  order: { localId: string; slug: string; tableNumber: number; createdAt: string; lines?: OrderLine[]; summary: string; estimatedTotal: number },
  response: OrderResponse,
  now: Date = new Date(),
): Promise<SentRecord[]> {
  const record: SentRecord = {
    localId: order.localId,
    folio: response.folio,
    orderId: response.id,
    slug: order.slug,
    tableNumber: order.tableNumber,
    createdAt: order.createdAt,
    sentAt: now.toISOString(),
    // Pedidos guardados por una versión anterior solo traen el resumen.
    lines: order.lines ?? [{ name: order.summary, quantity: 1, unitPrice: order.estimatedTotal, notes: '' }],
    estimatedTotal: order.estimatedTotal,
  };
  const run = queue.then(async () => {
    const current = (await readJson<SentRecord[]>(store, KEY)) ?? [];
    const next = prune([...current.filter((r) => r.localId !== record.localId), record], now);
    await writeJson(store, KEY, next);
    return next;
  });
  queue = run.catch(() => undefined);
  return run;
}

function prune(records: SentRecord[], now: Date): SentRecord[] {
  const cutoff = now.getTime() - HISTORY_TTL_MS;
  return records.filter((r) => new Date(r.sentAt).getTime() >= cutoff).slice(-HISTORY_MAX);
}
