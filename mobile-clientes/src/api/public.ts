import { request } from './client';
import type { OrderItemRequest, OrderResponse, PublicCatalog } from './types';

const base = (slug: string) => `/public/${encodeURIComponent(slug)}`;

export function fetchCatalog(slug: string): Promise<PublicCatalog> {
  return request<PublicCatalog>('GET', `${base(slug)}/catalog`);
}

export function placeTableOrder(slug: string, tableNumber: number, items: OrderItemRequest[]): Promise<OrderResponse> {
  return request<OrderResponse>('POST', `${base(slug)}/tables/${tableNumber}/orders`, { items });
}

export function callWaiter(slug: string, tableNumber: number): Promise<{ message: string }> {
  return request<{ message: string }>('POST', `${base(slug)}/tables/${tableNumber}/call-waiter`);
}
