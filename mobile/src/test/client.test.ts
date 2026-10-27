import { afterEach, beforeEach, describe, expect, it, vi, type Mock } from 'vitest';

const API = 'https://api.ejemplo.mx';

type Client = typeof import('../api/client');

/** Carga client.ts con una URL válida y un fetch simulado. */
async function loadClient(fetchMock: ReturnType<typeof vi.fn>, apiUrl = API): Promise<Client> {
  vi.resetModules();
  vi.stubEnv('EXPO_PUBLIC_API_URL', apiUrl);
  vi.stubGlobal('fetch', fetchMock);
  return import('../api/client');
}

const json = (status: number, body: unknown, contentType = 'application/json') =>
  new Response(body === undefined ? null : JSON.stringify(body), { status, headers: { 'Content-Type': contentType } });
const empty = (status: number) => new Response(null, { status });
const problem = (status: number, extra: Record<string, unknown> = {}) =>
  json(status, { type: 'about:blank', status, ...extra }, 'application/problem+json');

afterEach(() => {
  vi.useRealTimers();
  vi.resetModules();
});

describe('client: transporte', () => {
  it('arma la URL con /api/v1, envía la cookie y solo pone Content-Type cuando hay cuerpo', async () => {
    const fetchMock = vi.fn().mockImplementation(() => Promise.resolve(json(200, { ok: true })));
    const { request } = await loadClient(fetchMock);

    await request('GET', '/tables');
    await request('POST', '/orders', { body: { tableId: 't1', guestCount: 2 } });

    const [getUrl, getInit] = fetchMock.mock.calls[0];
    expect(getUrl).toBe(`${API}/api/v1/tables`);
    expect(getInit.credentials).toBe('include');
    expect(getInit.headers).toEqual({ Accept: 'application/json' });
    expect(getInit.body).toBeUndefined();

    const [, postInit] = fetchMock.mock.calls[1];
    expect(postInit.method).toBe('POST');
    expect(postInit.headers).toEqual({ Accept: 'application/json', 'Content-Type': 'application/json' });
    expect(postInit.body).toBe(JSON.stringify({ tableId: 't1', guestCount: 2 }));
  });

  it('devuelve el JSON de una respuesta correcta y undefined en un 204', async () => {
    const fetchMock = vi.fn().mockResolvedValueOnce(json(200, [{ id: 'm1' }])).mockResolvedValueOnce(empty(204));
    const { request } = await loadClient(fetchMock);

    await expect(request('GET', '/tables')).resolves.toEqual([{ id: 'm1' }]);
    await expect(request('POST', '/auth/logout', { skipRefresh: true })).resolves.toBeUndefined();
  });

  it('no llama al servidor si la configuración es inválida', async () => {
    const fetchMock = vi.fn();
    const { request } = await loadClient(fetchMock, 'http://inseguro.local');

    await expect(request('GET', '/tables')).rejects.toMatchObject({ kind: 'config' });
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('traduce una falla de red a un mensaje para el usuario', async () => {
    const { request } = await loadClient(vi.fn().mockRejectedValue(new TypeError('Network request failed')));

    await expect(request('GET', '/tables')).rejects.toMatchObject({
      kind: 'network',
      message: 'No se pudo conectar con el servidor. Revisa tu conexión a internet.',
      status: null,
    });
  });

  it('corta la petición a los 15 segundos y la reporta como tiempo agotado', async () => {
    vi.useFakeTimers();
    const fetchMock = vi.fn((_url: string, init: RequestInit) => new Promise<Response>((_resolve, reject) => {
      init.signal?.addEventListener('abort', () => reject(new DOMException('Aborted', 'AbortError')));
    }));
    const { request } = await loadClient(fetchMock);

    const pending = request('GET', '/tables');
    const assertion = expect(pending).rejects.toMatchObject({ kind: 'timeout', message: 'El servidor tardó demasiado en responder. Intenta de nuevo.' });
    await vi.advanceTimersByTimeAsync(14_999);
    expect((fetchMock.mock.calls[0][1] as RequestInit).signal?.aborted).toBe(false);
    await vi.advanceTimersByTimeAsync(1);
    await assertion;
  });
});

describe('client: traducción de errores HTTP', () => {
  const cases: Array<[string, () => Response, Record<string, unknown>]> = [
    ['400 con detalle y errores por campo', () => problem(400, { detail: 'La solicitud contiene campos invalidos.', errors: { guestCount: 'debe ser menor que o igual a 50' } }),
      { kind: 'validation', status: 400, message: 'La solicitud contiene campos invalidos.', fieldErrors: { guestCount: 'debe ser menor que o igual a 50' } }],
    ['400 sin cuerpo', () => empty(400), { kind: 'validation', status: 400, message: 'Revisa los datos enviados.', fieldErrors: {} }],
    ['403', () => problem(403, { detail: 'Detalle interno' }), { kind: 'forbidden', status: 403, message: 'Tu rol no tiene permiso para esta acción.' }],
    ['404 con detalle', () => problem(404, { detail: 'La mesa 9 no existe.' }), { kind: 'notFound', status: 404, message: 'La mesa 9 no existe.' }],
    ['404 sin cuerpo', () => empty(404), { kind: 'notFound', status: 404, message: 'El recurso solicitado no existe.' }],
    ['409 con detalle', () => problem(409, { detail: 'La mesa 4 no esta disponible (OCCUPIED).' }), { kind: 'conflict', status: 409, message: 'La mesa 4 no esta disponible (OCCUPIED).' }],
    ['409 sin cuerpo', () => empty(409), { kind: 'conflict', status: 409, message: 'La operación entra en conflicto con el estado actual.' }],
    ['429 con detalle', () => problem(429, { detail: 'Demasiados intentos fallidos. Intenta de nuevo en 15 minutos.' }), { kind: 'throttled', status: 429, message: 'Demasiados intentos fallidos. Intenta de nuevo en 15 minutos.' }],
    ['429 sin cuerpo', () => empty(429), { kind: 'throttled', status: 429, message: 'Demasiados intentos. Espera unos minutos.' }],
    ['500', () => problem(500, { detail: 'NullPointerException' }), { kind: 'server', status: 500, message: 'El servidor tuvo un problema. Intenta más tarde.' }],
    ['503 con cuerpo que no es JSON', () => new Response('<html>Service Unavailable</html>', { status: 503 }), { kind: 'server', status: 503, message: 'El servidor tuvo un problema. Intenta más tarde.' }],
  ];

  it.each(cases)('%s', async (_name, response, expected) => {
    const { request, ApiError } = await loadClient(vi.fn().mockImplementation(() => Promise.resolve(response())));
    const error = await request('GET', '/tables').catch((e: unknown) => e);
    expect(error).toBeInstanceOf(ApiError);
    expect(error).toMatchObject(expected);
  });
});

describe('client: renovación de la sesión ante un 401', () => {
  let expired: Mock<() => void>;
  beforeEach(() => { expired = vi.fn<() => void>(); });

  it('renueva una vez con /auth/refresh y repite la petición original', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(empty(401))
      .mockResolvedValueOnce(empty(204))
      .mockResolvedValueOnce(json(200, [{ id: 'm1' }]));
    const { request, setSessionExpiredHandler } = await loadClient(fetchMock);
    setSessionExpiredHandler(expired);

    await expect(request('GET', '/tables')).resolves.toEqual([{ id: 'm1' }]);
    expect(fetchMock.mock.calls.map(([url, init]) => `${init.method} ${url}`)).toEqual([
      `GET ${API}/api/v1/tables`,
      `POST ${API}/api/v1/auth/refresh`,
      `GET ${API}/api/v1/tables`,
    ]);
    expect(expired).not.toHaveBeenCalled();
  });

  it('si la renovación falla, avisa que la sesión expiró y no repite la petición', async () => {
    const fetchMock = vi.fn().mockResolvedValueOnce(empty(401)).mockResolvedValueOnce(empty(401));
    const { request, setSessionExpiredHandler } = await loadClient(fetchMock);
    setSessionExpiredHandler(expired);

    await expect(request('GET', '/tables')).rejects.toMatchObject({ kind: 'unauthorized', status: 401, message: 'Tu sesión expiró. Inicia sesión de nuevo.' });
    expect(fetchMock).toHaveBeenCalledTimes(2);
    expect(expired).toHaveBeenCalledTimes(1);
  });

  it('si la red falla durante la renovación, la trata como renovación fallida', async () => {
    const fetchMock = vi.fn().mockResolvedValueOnce(empty(401)).mockRejectedValueOnce(new TypeError('Network request failed'));
    const { request, setSessionExpiredHandler } = await loadClient(fetchMock);
    setSessionExpiredHandler(expired);

    await expect(request('GET', '/tables')).rejects.toMatchObject({ kind: 'unauthorized' });
    expect(expired).toHaveBeenCalledTimes(1);
  });

  it('varias peticiones con 401 a la vez comparten una sola renovación', async () => {
    let releaseRefresh!: (r: Response) => void;
    const refresh = new Promise<Response>((resolve) => { releaseRefresh = resolve; });
    const seen = new Map<string, number>();
    const fetchMock = vi.fn((url: string) => {
      if (url.endsWith('/auth/refresh')) return refresh;
      const n = (seen.get(url) ?? 0) + 1;
      seen.set(url, n);
      return Promise.resolve(n === 1 ? empty(401) : json(200, { url }));
    });
    const { request } = await loadClient(fetchMock);

    const a = request('GET', '/tables');
    const b = request('GET', '/products');
    // espera a que las dos peticiones reciban su 401 y pidan renovar
    await vi.waitFor(() => expect(seen.size).toBe(2));
    await new Promise((resolve) => setTimeout(resolve, 0));
    releaseRefresh(empty(204));

    await expect(Promise.all([a, b])).resolves.toEqual([{ url: `${API}/api/v1/tables` }, { url: `${API}/api/v1/products` }]);
    expect(fetchMock.mock.calls.filter(([u]) => u.endsWith('/auth/refresh'))).toHaveLength(1);
  });

  it('con skipRefresh no intenta renovar ni avisa expiración', async () => {
    const fetchMock = vi.fn().mockImplementation(() => Promise.resolve(empty(401)));
    const { request, setSessionExpiredHandler } = await loadClient(fetchMock);
    setSessionExpiredHandler(expired);

    await expect(request('POST', '/auth/logout', { skipRefresh: true })).rejects.toMatchObject({ kind: 'unauthorized' });
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(expired).not.toHaveBeenCalled();
  });
});
