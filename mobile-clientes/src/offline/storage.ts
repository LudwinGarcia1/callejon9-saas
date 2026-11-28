/**
 * Almacén clave-valor que usa la app para funcionar sin conexión. En la app es
 * AsyncStorage; en las pruebas se sustituye por un Map en memoria.
 */
export interface KeyValueStore {
  getItem(key: string): Promise<string | null>;
  setItem(key: string, value: string): Promise<void>;
  removeItem(key: string): Promise<void>;
}

export async function readJson<T>(store: KeyValueStore, key: string): Promise<T | null> {
  try {
    const raw = await store.getItem(key);
    return raw ? (JSON.parse(raw) as T) : null;
  } catch {
    // Un valor corrupto no debe impedir abrir la app: se trata como vacío.
    return null;
  }
}

export function writeJson(store: KeyValueStore, key: string, value: unknown): Promise<void> {
  return store.setItem(key, JSON.stringify(value));
}

export function memoryStore(): KeyValueStore {
  const data = new Map<string, string>();
  return {
    getItem: async (key) => data.get(key) ?? null,
    setItem: async (key, value) => void data.set(key, value),
    removeItem: async (key) => void data.delete(key),
  };
}
