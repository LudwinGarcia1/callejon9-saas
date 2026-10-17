import { request } from './client';
import type {
  AddOrderItemRequest,
  OpenOrderRequest,
  Order,
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

/** POST /api/v1/orders — abre una orden sobre una mesa libre (201). */
export function openOrder(body: OpenOrderRequest): Promise<Order> {
  return request<Order>('POST', '/orders', { body });
}

/** POST /api/v1/orders/{id}/items — agrega productos a la orden. */
export function addOrderItems(orderId: string, items: AddOrderItemRequest[]): Promise<Order> {
  return request<Order>('POST', `/orders/${encodeURIComponent(orderId)}/items`, { body: { items } });
}

/** POST /api/v1/orders/{id}/send-to-kitchen — pasa la orden a cocina. */
export function sendToKitchen(orderId: string): Promise<Order> {
  return request<Order>('POST', `/orders/${encodeURIComponent(orderId)}/send-to-kitchen`);
}

/** POST /api/v1/orders/{id}/cancel — se usa para no dejar una orden vacía abierta. */
export function cancelOrder(orderId: string): Promise<Order> {
  return request<Order>('POST', `/orders/${encodeURIComponent(orderId)}/cancel`);
}
