import { afterEach, describe, expect, it, vi } from 'vitest';

const API = 'https://api.ejemplo.mx';

async function loadAuth(fetchMock: ReturnType<typeof vi.fn>) {
  vi.resetModules();
  vi.stubEnv('EXPO_PUBLIC_API_URL', API);
  vi.stubGlobal('fetch', fetchMock);
  const auth = await import('../api/auth');
  const client = await import('../api/client');
  return { ...auth, ...client };
}

const empty = (status: number) => new Response(null, { status });
const credentials = { slug: 'pruebas', email: 'mesero@pruebas.com', password: 'una-contraseña-de-prueba' };

afterEach(() => {
  vi.resetModules();
});

describe('auth: login', () => {
  it('devuelve el usuario cuando las credenciales son válidas', async () => {
    const user = { userId: 'u1', fullName: 'Mesero de piso', role: 'WAITER', twoFactorRequired: false };
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify(user), { status: 200 }));
    const { login } = await loadAuth(fetchMock);

    await expect(login(credentials)).resolves.toEqual(user);
    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe(`${API}/api/v1/auth/login`);
    expect(JSON.parse(init.body)).toEqual(credentials);
  });

  it('ante un 401 muestra un único mensaje, sin decir qué dato falló ni intentar renovar', async () => {
    const fetchMock = vi.fn().mockResolvedValue(empty(401));
    const { login, LOGIN_FAILED_MESSAGE, setSessionExpiredHandler } = await loadAuth(fetchMock);
    const expired = vi.fn<() => void>();
    setSessionExpiredHandler(expired);

    await expect(login(credentials)).rejects.toMatchObject({ kind: 'unauthorized', status: 401, message: LOGIN_FAILED_MESSAGE });
    expect(LOGIN_FAILED_MESSAGE).toBe('Restaurante, correo o contraseña incorrectos.');
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(expired).not.toHaveBeenCalled();
  });

  it('un 429 conserva el mensaje del servidor sobre el bloqueo', async () => {
    const body = { status: 429, detail: 'Demasiados intentos fallidos. Intenta de nuevo en 15 minutos.' };
    const { login } = await loadAuth(vi.fn().mockResolvedValue(new Response(JSON.stringify(body), { status: 429 })));

    await expect(login(credentials)).rejects.toMatchObject({ kind: 'throttled', message: body.detail });
  });
});

describe('auth: logout', () => {
  it('llama a /auth/logout sin intentar renovar la sesión', async () => {
    const fetchMock = vi.fn().mockResolvedValue(empty(401));
    const { logout } = await loadAuth(fetchMock);

    await expect(logout()).resolves.toBeUndefined();
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(fetchMock.mock.calls[0][0]).toBe(`${API}/api/v1/auth/logout`);
  });

  it('termina sin error aunque el servidor no responda', async () => {
    const { logout } = await loadAuth(vi.fn().mockRejectedValue(new TypeError('Network request failed')));

    await expect(logout()).resolves.toBeUndefined();
  });
});

describe('auth: sesión guardada', () => {
  it('fetchCurrentUser consulta /auth/me y renueva si el token caducó', async () => {
    const me = { userId: 'u1', fullName: 'Mesero de piso', role: 'WAITER', restaurantName: 'Callejon 9 pruebas' };
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(empty(401))
      .mockResolvedValueOnce(empty(204))
      .mockResolvedValueOnce(new Response(JSON.stringify(me), { status: 200 }));
    const { fetchCurrentUser } = await loadAuth(fetchMock);

    await expect(fetchCurrentUser()).resolves.toEqual(me);
    expect(fetchMock.mock.calls.map(([url]) => url)).toEqual([
      `${API}/api/v1/auth/me`, `${API}/api/v1/auth/refresh`, `${API}/api/v1/auth/me`,
    ]);
  });
});
