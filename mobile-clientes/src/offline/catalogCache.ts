import { isOfflineError } from '../api/client';
import type { PublicCatalog } from '../api/types';
import { readJson, writeJson, type KeyValueStore } from './storage';

/**
 * El menú se guarda en el teléfono cada vez que se descarga. Sin conexión se
 * muestra la última copia y se indica de cuándo es.
 */

interface CachedCatalog {
  savedAt: string;
  catalog: PublicCatalog;
}

export interface LoadedCatalog {
  catalog: PublicCatalog;
  source: 'network' | 'cache';
  savedAt: string;
}

const key = (slug: string) => `catalog:${slug}`;

export async function readCachedCatalog(store: KeyValueStore, slug: string): Promise<CachedCatalog | null> {
  return readJson<CachedCatalog>(store, key(slug));
}

/**
 * Descarga el menú y lo guarda. Si no hay conexión (o el servidor no responde
 * a tiempo) devuelve la copia guardada; si no existe ninguna, propaga el error.
 * Un 404 sí borra la copia: el restaurante dejó de existir.
 */
export async function loadCatalog(
  store: KeyValueStore,
  slug: string,
  fetcher: (slug: string) => Promise<PublicCatalog>,
  now: () => Date = () => new Date(),
): Promise<LoadedCatalog> {
  try {
    const catalog = await fetcher(slug);
    const savedAt = now().toISOString();
    await writeJson(store, key(slug), { savedAt, catalog } satisfies CachedCatalog);
    return { catalog, source: 'network', savedAt };
  } catch (error) {
    if (isOfflineError(error) || isTimeout(error)) {
      const cached = await readCachedCatalog(store, slug);
      if (cached) {
        return { catalog: cached.catalog, source: 'cache', savedAt: cached.savedAt };
      }
    } else if (isNotFound(error)) {
      await store.removeItem(key(slug));
    }
    throw error;
  }
}

function isTimeout(error: unknown): boolean {
  return (error as { kind?: string })?.kind === 'timeout';
}

function isNotFound(error: unknown): boolean {
  return (error as { kind?: string })?.kind === 'notFound';
}
