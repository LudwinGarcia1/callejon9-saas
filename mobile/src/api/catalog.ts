import { request } from './client';
import type {
  Category,
  CreateProductRequest,
  Product,
  UpdateProductRequest,
  UpdateProductStatusRequest,
} from './types';

/**
 * Catálogo del restaurante. Igual que en waiter.ts, el restaurante no viaja
 * en la petición: sale del JWT firmado y PostgreSQL filtra con RLS.
 */

/** GET /api/v1/categories — categorías del menú, en su orden. */
export function fetchCategories(): Promise<Category[]> {
  return request<Category[]>('GET', '/categories');
}

/**
 * GET /api/v1/products — catálogo completo. Con `includeInactive` también
 * devuelve los productos dados de baja, para que ADMIN pueda reactivarlos.
 */
export function fetchCatalog(includeInactive: boolean): Promise<Product[]> {
  return request<Product[]>('GET', includeInactive ? '/products?includeInactive=true' : '/products');
}

/** POST /api/v1/products — registra un producto (201). Solo ADMIN. */
export function createProduct(body: CreateProductRequest): Promise<Product> {
  return request<Product>('POST', '/products', { body });
}

/** PUT /api/v1/products/{id} — corrige nombre, precio, descripción o categoría. Solo ADMIN. */
export function updateProduct(id: string, body: UpdateProductRequest): Promise<Product> {
  return request<Product>('PUT', `/products/${encodeURIComponent(id)}`, { body });
}

/** PATCH /api/v1/products/{id} — da de alta o de baja el producto (baja lógica). Solo ADMIN. */
export function setProductActive(id: string, active: boolean): Promise<Product> {
  const body: UpdateProductStatusRequest = { active };
  return request<Product>('PATCH', `/products/${encodeURIComponent(id)}`, { body });
}
