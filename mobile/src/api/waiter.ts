import { request } from './client';
import type {
  Product,
  RestaurantTable,
} from './types';

/**
 * Operaciones del mesero. El restaurante nunca viaja en la petición: el
 * backend lo toma del JWT firmado y PostgreSQL filtra con RLS.
 */

/** GET /api/v1/tables — mesas activas del restaurante. */
export function fetchTables(): Promise<RestaurantTable[]> {
  return request<RestaurantTable[]>('GET', '/tables');
}

/** GET /api/v1/products — menú activo. */
export function fetchProducts(): Promise<Product[]> {
  return request<Product[]>('GET', '/products');
}
