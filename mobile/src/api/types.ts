/** Contratos JSON del backend (/api/v1). Los nombres replican los DTO de Java. */

export type Role = 'SUPER_ADMIN' | 'ADMIN' | 'WAITER' | 'KITCHEN' | 'CASHIER';

export interface LoginRequest {
  slug: string;
  email: string;
  password: string;
}

export interface LoginResponse {
  userId: string;
  fullName: string;
  role: Role;
  twoFactorRequired: boolean;
}

export interface MeResponse {
  userId: string;
  fullName: string;
  role: Role;
  tenantId: string;
  slug: string;
  restaurantName: string;
}

export type TableStatus = 'FREE' | 'OCCUPIED' | 'RESERVED' | 'CLEANING';

export interface RestaurantTable {
  id: string;
  number: number;
  capacity: number;
  status: TableStatus;
  waiterId: string | null;
  active: boolean;
}

export interface Product {
  id: string;
  name: string;
  description: string | null;
  price: number;
  categoryId: string;
  active: boolean;
}

export interface Category {
  id: string;
  name: string;
  sortOrder: number;
}

export interface CreateProductRequest {
  name: string;
  description?: string;
  price: number;
  categoryId?: string;
}

export interface OpenOrderRequest {
  tableId: string;
  guestCount: number;
}

export interface AddOrderItemRequest {
  productId: string;
  quantity: number;
  notes?: string;
}

export interface OrderItem {
  id: string;
  productId: string;
  productName: string;
  unitPrice: number;
  quantity: number;
  kitchenStatus: string;
  notes: string | null;
}

export type OrderStatus = 'NEW' | 'SENT' | 'READY' | 'PAID' | 'CANCELED';

/** Página de un listado (CAL-16): PageResponse del backend. `page` empieza en 0. */
export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

/** GET /api/v1/orders — resumen sin partidas (OrderSummaryResponse). */
export interface OrderSummary {
  id: string;
  folio: string;
  tableId: string | null;
  waiterId: string;
  guestCount: number;
  status: OrderStatus;
  total: number;
  openedAt: string;
}

export interface Order {
  id: string;
  folio: string;
  tableId: string;
  waiterId: string;
  guestCount: number;
  status: OrderStatus;
  total: number;
  openedAt: string;
  sentToKitchenAt: string | null;
  closedAt: string | null;
  items: OrderItem[];
}

/** Cuerpo RFC 7807 que devuelve el backend en los errores. */
export interface ProblemDetail {
  title?: string;
  detail?: string;
  status?: number;
  errors?: Record<string, string>;
}
