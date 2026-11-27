import { describe, expect, it, vi } from 'vitest';
import { ApiError } from '../api/client';
import type { PublicCatalog } from '../api/types';
import { loadCatalog, readCachedCatalog } from '../offline/catalogCache';
import { memoryStore } from '../offline/storage';

const catalog: PublicCatalog = { restaurantName: 'Callejón 9', slug: 'c9', categories: [], products: [] };
const now = () => new Date('2026-11-25T18:00:00Z');

describe('menú sin conexión', () => {
  it('guarda el menú descargado', async () => {
    const store = memoryStore();
    const result = await loadCatalog(store, 'c9', async () => catalog, now);
    expect(result).toEqual({ catalog, source: 'network', savedAt: '2026-11-25T18:00:00.000Z' });
    expect((await readCachedCatalog(store, 'c9'))?.catalog).toEqual(catalog);
  });

  it.each(['network', 'timeout'] as const)('sin red (%s) devuelve la copia guardada', async (kind) => {
    const store = memoryStore();
    await loadCatalog(store, 'c9', async () => catalog, now);
    const result = await loadCatalog(store, 'c9', () => Promise.reject(new ApiError(kind, 'x')));
    expect(result.source).toBe('cache');
    expect(result.savedAt).toBe('2026-11-25T18:00:00.000Z');
  });

  it('sin red y sin copia propaga el error', async () => {
    await expect(loadCatalog(memoryStore(), 'c9', () => Promise.reject(new ApiError('network', 'x')))).rejects.toThrow('x');
  });

  it('un 404 borra la copia del restaurante', async () => {
    const store = memoryStore();
    await loadCatalog(store, 'c9', async () => catalog, now);
    await expect(loadCatalog(store, 'c9', () => Promise.reject(new ApiError('notFound', 'no existe')))).rejects.toThrow();
    expect(await readCachedCatalog(store, 'c9')).toBeNull();
  });

  it('ignora una copia corrupta', async () => {
    const store = memoryStore();
    await store.setItem('catalog:c9', '{no es json');
    expect(await readCachedCatalog(store, 'c9')).toBeNull();
    const fetcher = vi.fn().mockRejectedValue(new ApiError('network', 'sin red'));
    await expect(loadCatalog(store, 'c9', fetcher)).rejects.toThrow('sin red');
  });
});
