import { request } from './client';
import type { Category, CreateProductRequest, Product } from './types';

/**
 * Catálogo del restaurante. Igual que en waiter.ts, el restaurante no viaja
 * en la petición: sale del JWT firmado y PostgreSQL filtra con RLS.
 */

/** GET /api/v1/categories — categorías del menú, en su orden. */
export function fetchCategories(): Promise<Category[]> {
  return request<Category[]>('GET', '/categories');
}

/** POST /api/v1/products — registra un producto (201). Solo ADMIN. */
export function createProduct(body: CreateProductRequest): Promise<Product> {
  return request<Product>('POST', '/products', { body });
}
