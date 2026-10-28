import { afterEach, describe, expect, it, vi } from 'vitest';

const API = 'https://api.ejemplo.mx';

afterEach(() => {
  vi.resetModules();
  vi.unstubAllGlobals();
  vi.unstubAllEnvs();
});

describe('waiter: órdenes abiertas paginadas (CAL-16)', () => {
  it('pide solo las abiertas en la página más grande y devuelve su contenido', async () => {
    const orders = [{ id: 'o1', folio: 'ORD-1', tableId: 't1', waiterId: 'u1', guestCount: 2, status: 'SENT', total: 50, openedAt: '2026-10-28T20:00:00Z' }];
    const fetchMock = vi.fn().mockResolvedValue(new Response(
      JSON.stringify({ content: orders, page: 0, size: 100, totalElements: 1, totalPages: 1 }),
      { status: 200, headers: { 'Content-Type': 'application/json' } }));
    vi.resetModules();
    vi.stubEnv('EXPO_PUBLIC_API_URL', API);
    vi.stubGlobal('fetch', fetchMock);
    const { fetchOpenOrders } = await import('../api/waiter');

    await expect(fetchOpenOrders()).resolves.toEqual(orders);
    expect(fetchMock.mock.calls[0][0]).toBe(`${API}/api/v1/orders?open=true&size=100`);
  });
});
