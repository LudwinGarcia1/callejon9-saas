import { afterEach, describe, expect, it, vi } from 'vitest';

/** Carga config.ts de nuevo con las variables de entorno y el __DEV__ indicados. */
async function loadConfig(env: Record<string, string>, dev = true) {
  vi.resetModules();
  vi.stubGlobal('__DEV__', dev);
  for (const [name, value] of Object.entries(env)) vi.stubEnv(name, value);
  return import('../config');
}

afterEach(() => {
  vi.resetModules();
});

describe('config: URL del servidor', () => {
  it('acepta una URL https y le quita las diagonales finales', async () => {
    const config = await loadConfig({ EXPO_PUBLIC_API_URL: 'https://api.ejemplo.mx//' });
    expect(config.API_URL).toBe('https://api.ejemplo.mx');
    expect(config.CONFIG_ERROR).toBeNull();
    expect(config.IS_SECURE_TRANSPORT).toBe(true);
  });

  it('informa el error cuando falta EXPO_PUBLIC_API_URL', async () => {
    const config = await loadConfig({ EXPO_PUBLIC_API_URL: '   ' });
    expect(config.API_URL).toBe('');
    expect(config.CONFIG_ERROR).toBe('Falta EXPO_PUBLIC_API_URL en el archivo .env.');
  });

  it('rechaza http:// en desarrollo si no se habilitó explícitamente', async () => {
    const config = await loadConfig({ EXPO_PUBLIC_API_URL: 'http://192.168.1.10:8080', EXPO_PUBLIC_ALLOW_INSECURE_HTTP: 'false' });
    expect(config.API_URL).toBe('');
    expect(config.CONFIG_ERROR).toBe('La URL del servidor debe usar https:// para proteger las credenciales.');
    expect(config.IS_SECURE_TRANSPORT).toBe(false);
  });

  it('acepta http:// solo en desarrollo y con EXPO_PUBLIC_ALLOW_INSECURE_HTTP=true', async () => {
    const config = await loadConfig({ EXPO_PUBLIC_API_URL: 'http://192.168.1.10:8080', EXPO_PUBLIC_ALLOW_INSECURE_HTTP: 'true' });
    expect(config.API_URL).toBe('http://192.168.1.10:8080');
    expect(config.CONFIG_ERROR).toBeNull();
    expect(config.IS_SECURE_TRANSPORT).toBe(false);
  });

  it('rechaza http:// en una build de producción aunque se pida permitirlo', async () => {
    const config = await loadConfig({ EXPO_PUBLIC_API_URL: 'http://192.168.1.10:8080', EXPO_PUBLIC_ALLOW_INSECURE_HTTP: 'true' }, false);
    expect(config.API_URL).toBe('');
    expect(config.CONFIG_ERROR).toBe('La URL del servidor debe usar https:// para proteger las credenciales.');
  });

  it('rechaza cualquier otro esquema', async () => {
    const config = await loadConfig({ EXPO_PUBLIC_API_URL: 'ftp://api.ejemplo.mx' });
    expect(config.CONFIG_ERROR).toBe('La URL del servidor debe usar https:// para proteger las credenciales.');
  });

  it('fija el tiempo límite de cada petición en 15 segundos', async () => {
    const config = await loadConfig({ EXPO_PUBLIC_API_URL: 'https://api.ejemplo.mx' });
    expect(config.REQUEST_TIMEOUT_MS).toBe(15_000);
  });
});
