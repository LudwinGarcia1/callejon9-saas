import { afterEach, describe, expect, it, vi } from 'vitest';

async function loadClient() {
  vi.resetModules();
  vi.stubEnv('EXPO_PUBLIC_API_URL', 'https://api.ejemplo.mx');
  return import('../api/client');
}

afterEach(() => vi.resetModules());

describe('cliente HTTP público', () => {
  it('llama la ruta pública sin enviar cookies', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({ ok: 1 }), { status: 200 }));
    vi.stubGlobal('fetch', fetchMock);
    const { request } = await loadClient();
    await request('GET', '/public/c9/catalog');
    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe('https://api.ejemplo.mx/api/v1/public/c9/catalog');
    expect(init.credentials).toBe('omit');
  });

  it('distingue la falta de red, que sí se puede reintentar', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Network request failed')));
    const { request, isOfflineError } = await loadClient();
    const error = (await request('GET', '/x').catch((e: unknown) => e)) as { kind: string; message: string };
    expect(error.kind).toBe('network');
    expect(isOfflineError(error)).toBe(true);
  });

  it('traduce un 400 con el detalle del problema', async () => {
    const body = JSON.stringify({ detail: 'El producto Taco no esta disponible.' });
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(body, { status: 400 })));
    const { request, isOfflineError } = await loadClient();
    const error = (await request('POST', '/x', {}).catch((e: unknown) => e)) as { kind: string; message: string };
    expect(error.kind).toBe('validation');
    expect(error.message).toBe('El producto Taco no esta disponible.');
    expect(isOfflineError(error)).toBe(false);
  });

  it('rechaza http:// en producción', async () => {
    vi.resetModules();
    vi.stubGlobal('__DEV__', false);
    vi.stubEnv('EXPO_PUBLIC_API_URL', 'http://192.168.1.10:8080');
    vi.stubEnv('EXPO_PUBLIC_ALLOW_INSECURE_HTTP', 'true');
    const { request } = await import('../api/client');
    await expect(request('GET', '/x')).rejects.toMatchObject({ kind: 'config' });
  });
});

describe('tiempo límite', () => {
  it('rechaza como timeout aunque fetch nunca termine', async () => {
    vi.useFakeTimers();
    try {
      vi.stubGlobal('fetch', vi.fn(() => new Promise(() => undefined)));
      const { request } = await loadClient();
      const pending = request('GET', '/x').catch((e: unknown) => e) as Promise<{ kind: string }>;
      await vi.advanceTimersByTimeAsync(12_000);
      expect((await pending).kind).toBe('timeout');
    } finally {
      vi.useRealTimers();
    }
  });
});
