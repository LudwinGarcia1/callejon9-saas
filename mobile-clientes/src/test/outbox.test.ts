import { describe, expect, it, vi } from 'vitest';
import { ApiError } from '../api/client';
import type { OrderResponse } from '../api/types';
import { discard, enqueue, flush, listPending, sendOne } from '../offline/outbox';
import { memoryStore } from '../offline/storage';

const WINDOW = 30 * 60_000;
const created = new Date('2026-11-25T18:00:00Z');
const later = (minutes: number) => new Date(created.getTime() + minutes * 60_000);
const response: OrderResponse = { id: 'o1', folio: 'F-001', status: 'SENT', total: 50 };

async function queued(count = 1) {
  const store = memoryStore();
  for (let i = 0; i < count; i++) {
    await enqueue(store, { slug: 'c9', tableNumber: 3, items: [{ productId: 'p1', quantity: 2 }], summary: '2 × Taco', estimatedTotal: 50 }, created);
  }
  return store;
}

describe('pedidos guardados sin conexión', () => {
  it('envía los pendientes y los saca de la bandeja', async () => {
    const store = await queued(2);
    const send = vi.fn().mockResolvedValue(response);
    const result = await flush(store, send, { now: later(5), autoSendWindowMs: WINDOW });
    expect(send).toHaveBeenCalledTimes(2);
    expect(result.sent).toHaveLength(2);
    expect(await listPending(store)).toEqual([]);
  });

  it('sin red conserva el pedido y no intenta los siguientes', async () => {
    const store = await queued(2);
    const send = vi.fn().mockRejectedValue(new ApiError('network', 'sin red'));
    const result = await flush(store, send, { now: later(5), autoSendWindowMs: WINDOW });
    expect(send).toHaveBeenCalledTimes(1);
    expect(result.remaining.map((o) => o.status)).toEqual(['pending', 'pending']);
  });

  it('un timeout deja el pedido sin confirmar y no se reintenta solo', async () => {
    const store = await queued();
    const send = vi.fn().mockRejectedValue(new ApiError('timeout', 'lento'));
    await flush(store, send, { now: later(1), autoSendWindowMs: WINDOW });
    expect((await listPending(store))[0].status).toBe('unconfirmed');
    await flush(store, send, { now: later(2), autoSendWindowMs: WINDOW });
    expect(send).toHaveBeenCalledTimes(1);
  });

  it('no envía solo un pedido viejo: lo marca como vencido', async () => {
    const store = await queued();
    const send = vi.fn();
    await flush(store, send, { now: later(31), autoSendWindowMs: WINDOW });
    expect(send).not.toHaveBeenCalled();
    expect((await listPending(store))[0].status).toBe('expired');
  });

  it('guarda el motivo cuando el servidor rechaza el pedido', async () => {
    const store = await queued();
    const send = vi.fn().mockRejectedValue(new ApiError('validation', 'El producto Taco no esta disponible.'));
    await flush(store, send, { now: later(1), autoSendWindowMs: WINDOW });
    const [order] = await listPending(store);
    expect(order.status).toBe('rejected');
    expect(order.message).toBe('El producto Taco no esta disponible.');
  });

  it('el comensal puede enviar a mano un vencido o descartarlo', async () => {
    const store = await queued(2);
    await flush(store, vi.fn(), { now: later(40), autoSendWindowMs: WINDOW });
    const [first, second] = await listPending(store);
    const result = await sendOne(store, first.localId, vi.fn().mockResolvedValue(response));
    expect(result.sent?.order.folio).toBe('F-001');
    expect(await discard(store, second.localId)).toEqual([]);
  });
});

describe('bandeja con operaciones simultáneas', () => {
  it('no pierde un pedido guardado mientras se envían los anteriores', async () => {
    const store = await queued();
    let release!: (value: OrderResponse) => void;
    const send = vi.fn(() => new Promise<OrderResponse>((resolve) => (release = resolve)));
    const flushing = flush(store, send, { now: later(1), autoSendWindowMs: WINDOW });
    await vi.waitFor(() => expect(send).toHaveBeenCalled());
    const added = await enqueue(store, { slug: 'c9', tableNumber: 3, items: [{ productId: 'p2', quantity: 1 }], summary: '1 × Agua', estimatedTotal: 18 }, later(1));
    release(response);
    const result = await flushing;
    expect(result.sent).toHaveLength(1);
    expect((await listPending(store)).map((o) => o.localId)).toEqual([added.localId]);
  });
});
