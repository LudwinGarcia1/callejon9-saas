import { describe, expect, it, vi } from 'vitest';
import { ApiError } from '../api/client';
import type { OrderResponse } from '../api/types';
import { HISTORY_MAX, historyForTable, listHistory, recordSent } from '../offline/orderHistory';
import { enqueue, flush, sendOne } from '../offline/outbox';
import { memoryStore } from '../offline/storage';

const at = (minutes: number) => new Date(Date.UTC(2026, 10, 25, 18, minutes));
const response = (folio: string): OrderResponse => ({ id: `o-${folio}`, folio, status: 'SENT', total: 999 });
const lines = [{ name: 'Taco', quantity: 2, unitPrice: 25, notes: 'sin cebolla' }];

function order(localId: string, tableNumber = 3) {
  return { localId, slug: 'c9', tableNumber, createdAt: at(0).toISOString(), lines, summary: '2 × Taco', estimatedTotal: 50 };
}

describe('historial de pedidos enviados', () => {
  it('guarda lo que pidió el comensal, no el total de la mesa', async () => {
    const store = memoryStore();
    await recordSent(store, order('a'), response('F-1'), at(1));
    const [record] = await listHistory(store, at(2));
    expect(record).toMatchObject({ folio: 'F-1', lines, estimatedTotal: 50, sentAt: at(1).toISOString() });
  });

  it('filtra por mesa y ordena del más reciente al más antiguo', async () => {
    const store = memoryStore();
    await recordSent(store, order('a'), response('F-1'), at(1));
    await recordSent(store, order('b', 7), response('F-2'), at(2));
    await recordSent(store, order('c'), response('F-3'), at(3));
    const folios = (await historyForTable(store, 'c9', 3, at(4))).map((r) => r.folio);
    expect(folios).toEqual(['F-3', 'F-1']);
  });

  it('descarta lo de más de un día y conserva como máximo los últimos registros', async () => {
    const store = memoryStore();
    await recordSent(store, order('viejo'), response('F-0'), at(0));
    const nextDay = new Date(at(0).getTime() + 25 * 60 * 60_000);
    expect(await listHistory(store, nextDay)).toEqual([]);
    for (let i = 0; i < HISTORY_MAX + 5; i++) await recordSent(store, order(`n${i}`), response(`F-${i}`), nextDay);
    expect(await listHistory(store, nextDay)).toHaveLength(HISTORY_MAX);
  });

  it('acepta pedidos guardados por una versión anterior, sin líneas', async () => {
    const store = memoryStore();
    const { lines: _omit, ...legacy } = order('a');
    await recordSent(store, legacy, response('F-1'), at(1));
    expect((await listHistory(store, at(2)))[0].lines).toEqual([{ name: '2 × Taco', quantity: 1, unitPrice: 50, notes: '' }]);
  });
});

describe('la bandeja alimenta el historial', () => {
  async function queued() {
    const store = memoryStore();
    const pending = await enqueue(store, { slug: 'c9', tableNumber: 3, items: [{ productId: 'p1', quantity: 2 }], summary: '2 × Taco', lines, estimatedTotal: 50 }, new Date());
    return { store, pending };
  }

  it('al enviarse un pedido guardado, aparece en el historial', async () => {
    const { store } = await queued();
    await flush(store, vi.fn().mockResolvedValue(response('F-9')), { autoSendWindowMs: 30 * 60_000 });
    const history = await historyForTable(store, 'c9', 3);
    expect(history.map((r) => r.folio)).toEqual(['F-9']);
    expect(history[0].lines).toEqual(lines);
  });

  it('si el envío falla, no aparece en el historial', async () => {
    const { store, pending } = await queued();
    await sendOne(store, pending.localId, vi.fn().mockRejectedValue(new ApiError('network', 'sin red')));
    await sendOne(store, pending.localId, vi.fn().mockRejectedValue(new ApiError('timeout', 'lento')));
    expect(await listHistory(store)).toEqual([]);
  });
});
