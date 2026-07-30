/**
 * Tipos del contrato de API conocido hasta ahora. Los endpoints todavia estan
 * en construccion en el backend; este archivo solo describe las formas de
 * datos, no invoca nada.
 */

/** Roles exactos que reconoce el backend. */
export type UserRole = "SUPER_ADMIN" | "ADMIN" | "WAITER" | "KITCHEN" | "CASHIER";

/**
 * RFC 7807 Problem Details devuelto por el backend en respuestas de error.
 * `errors` mapea el nombre de un campo a su mensaje de validacion.
 */
export interface ProblemDetail {
  title?: string;
  detail?: string;
  status?: number;
  errors?: Record<string, string>;
}

/** POST /api/v1/signup */
export interface SignupRequest {
  restaurantName: string;
  /** Debe cumplir ^[a-z0-9-]{3,80}$ */
  slug: string;
  adminEmail: string;
  adminFullName: string;
  password: string;
  planCode: string;
}

export interface SignupResponse {
  tenantId: string;
  slug: string;
  adminEmail: string;
}

/**
 * POST /api/v1/auth/login
 * El slug es obligatorio porque el email solo es unico dentro de un
 * restaurante, no en toda la plataforma.
 */
export interface LoginRequest {
  slug: string;
  email: string;
  password: string;
}

export interface LoginResponse {
  userId: string;
  fullName: string;
  role: UserRole;
  twoFactorRequired: boolean;
}

/** GET /api/v1/platform/plans (requiere rol SUPER_ADMIN) */
export interface Plan {
  code: string;
  name: string;
  priceMonthly: number;
  maxUsers: number;
  maxTables: number;
}

// ---------------------------------------------------------------------------
// Dominio: mesas, catalogo, ordenes. Todos los enums llegan del backend como
// strings, no como numeros.
// ---------------------------------------------------------------------------

export type TableStatus = "FREE" | "OCCUPIED" | "RESERVED" | "CLEANING";

export type OrderStatus = "NEW" | "SENT" | "READY" | "PAID" | "CANCELED";

export type KitchenItemStatus =
  | "PENDING"
  | "IN_PREPARATION"
  | "READY"
  | "DELIVERED";

export type PaymentMethod = "CASH" | "CARD" | "TRANSFER" | "MIXED" | "MERCADOPAGO";

export interface TableResponse {
  id: string;
  number: number;
  capacity: number;
  status: TableStatus;
  waiterId: string | null;
}

export interface CategoryResponse {
  id: string;
  name: string;
  sortOrder: number;
}

export interface ProductResponse {
  id: string;
  name: string;
  description: string | null;
  price: number;
  categoryId: string | null;
  active: boolean;
}

export interface OrderItemResponse {
  id: string;
  productId: string | null;
  productName: string;
  unitPrice: number;
  quantity: number;
  kitchenStatus: KitchenItemStatus;
  notes: string | null;
}

export interface OrderResponse {
  id: string;
  folio: string;
  tableId: string | null;
  waiterId: string | null;
  guestCount: number;
  status: OrderStatus;
  total: number;
  openedAt: string;
  sentToKitchenAt: string | null;
  closedAt: string | null;
  items: OrderItemResponse[];
}

/** Igual que OrderResponse pero sin `items`, `sentToKitchenAt` ni `closedAt` (listados). */
export type OrderSummaryResponse = Omit<
  OrderResponse,
  "items" | "sentToKitchenAt" | "closedAt"
>;

export interface CreateTableRequest {
  number: number;
  capacity: number;
}

export interface CreateCategoryRequest {
  name: string;
  sortOrder?: number;
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

export interface AddOrderItemsRequest {
  items: {
    productId: string;
    quantity: number;
    notes?: string;
  }[];
}

// ---------------------------------------------------------------------------
// No verificado todavia contra el backend: este trabajo esta aterrizando en
// paralelo en el workstream de backend. Escrito contra el contrato esperado;
// puede requerir ajustes cuando los endpoints existan de verdad.
// ---------------------------------------------------------------------------

/** GET /api/v1/auth/me — todavia no existe en el backend. */
export interface SessionResponse {
  userId: string;
  fullName: string;
  role: UserRole;
  tenantId: string;
  slug: string;
  restaurantName: string;
}

/** GET /api/v1/tickets/{id} — mejor esfuerzo, no verificado. */
export interface TicketResponse {
  id: string;
  orderId: string;
  folio: string;
  total: number;
  paymentMethod: PaymentMethod;
  tipAmount: number;
  issuedAt: string;
}

/** POST /api/v1/orders/{id}/checkout — mejor esfuerzo, no verificado. */
export interface CheckoutRequest {
  paymentMethod: PaymentMethod;
  tipPercent: number;
}

/** GET /api/v1/kitchen/orders — mejor esfuerzo, no verificado. */
export interface KitchenOrder {
  orderId: string;
  folio: string;
  tableNumber: number | null;
  openedAt: string;
  items: OrderItemResponse[];
}

// ---------------------------------------------------------------------------
// Etiquetas en espanol. Este archivo es el unico lugar donde los
// identificadores en ingles y la copia en espanol se encuentran.
// ---------------------------------------------------------------------------

export const ORDER_STATUS_LABELS: Record<OrderStatus, string> = {
  NEW: "Nueva",
  SENT: "Enviada a cocina",
  READY: "Lista",
  PAID: "Pagada",
  CANCELED: "Cancelada",
};

export const TABLE_STATUS_LABELS: Record<TableStatus, string> = {
  FREE: "Libre",
  OCCUPIED: "Ocupada",
  RESERVED: "Reservada",
  CLEANING: "En limpieza",
};

export const KITCHEN_STATUS_LABELS: Record<KitchenItemStatus, string> = {
  PENDING: "Pendiente",
  IN_PREPARATION: "En preparación",
  READY: "Listo",
  DELIVERED: "Entregado",
};

export const PAYMENT_METHOD_LABELS: Record<PaymentMethod, string> = {
  CASH: "Efectivo",
  CARD: "Tarjeta",
  TRANSFER: "Transferencia",
  MIXED: "Mixto",
  MERCADOPAGO: "MercadoPago",
};
