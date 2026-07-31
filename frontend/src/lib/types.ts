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
  active: boolean;
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

/** PUT /api/v1/products/{id} — verificado contra UpdateProductRequest. El
 * precio nuevo nunca toca las lineas de ordenes ya existentes: cada una
 * conserva su propia copia del precio al momento de agregarse. */
export interface UpdateProductRequest {
  name: string;
  description?: string;
  price: number;
  categoryId?: string;
}

/** PATCH /api/v1/products/{id} — verificado contra UpdateProductStatusRequest. */
export interface UpdateProductStatusRequest {
  active: boolean;
}

/** PUT /api/v1/categories/{id} — verificado contra UpdateCategoryRequest.
 * A diferencia del alta, aqui `sortOrder` es obligatorio. */
export interface UpdateCategoryRequest {
  name: string;
  sortOrder: number;
}

/** PUT /api/v1/tables/{id} — verificado contra UpdateTableRequest. */
export interface UpdateTableRequest {
  number: number;
  capacity: number;
}

/** PATCH /api/v1/tables/{id} — verificado contra UpdateTableStatusRequest. */
export interface UpdateTableStatusRequest {
  active: boolean;
}

/**
 * GET/POST /api/v1/users — verificado contra UserResponse en
 * backend/src/main/java/com/callejon9/user/web/dto. Nunca incluye datos de
 * autenticacion (hash de password, secreto TOTP).
 */
export interface UserResponse {
  id: string;
  email: string;
  fullName: string;
  role: UserRole;
  active: boolean;
}

/** POST /api/v1/users — verificado contra CreateUserRequest. El rol nunca
 * puede ser SUPER_ADMIN: el backend lo rechaza con 400. */
export interface CreateUserRequest {
  email: string;
  fullName: string;
  role: UserRole;
  password: string;
}

/** PATCH /api/v1/users/{id} — verificado contra UpdateUserStatusRequest. */
export interface UpdateUserStatusRequest {
  active: boolean;
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

/** GET /api/v1/auth/me — verificado contra AuthController.me / MeResponse. */
export interface SessionResponse {
  userId: string;
  fullName: string;
  role: UserRole;
  tenantId: string;
  slug: string;
  restaurantName: string;
}

// ---------------------------------------------------------------------------
// Cocina, cobro y tickets: verificado contra KitchenController/KitchenService,
// CheckoutController/CheckoutService y TicketController en
// backend/src/main/java/com/callejon9/{kitchen,sale,ticket}.
// ---------------------------------------------------------------------------

/** GET /api/v1/kitchen/orders — verificado contra KitchenItemResponse. */
export interface KitchenItemResponse {
  id: string;
  orderId: string;
  productId: string;
  productName: string;
  quantity: number;
  kitchenStatus: KitchenItemStatus;
  notes: string | null;
}

/** GET /api/v1/kitchen/orders — verificado contra KitchenOrderResponse. */
export interface KitchenOrderResponse {
  id: string;
  folio: string;
  tableId: string | null;
  status: OrderStatus;
  sentToKitchenAt: string | null;
  items: KitchenItemResponse[];
}

/** POST /api/v1/kitchen/items/{itemId}/status — verificado contra UpdateKitchenItemStatusRequest. */
export interface UpdateKitchenItemStatusRequest {
  status: KitchenItemStatus;
}

/** POST /api/v1/orders/{id}/checkout — verificado contra CheckoutRequest (sale). */
export interface CheckoutRequest {
  paymentMethod: PaymentMethod;
  tipPercent: number;
}

/** Fotografia inmutable de una linea del ticket — verificado contra TicketItemSnapshot. */
export interface TicketItemSnapshot {
  productId: string;
  productName: string;
  unitPrice: number;
  quantity: number;
  subtotal: number;
}

/** GET /api/v1/tickets/{id}, GET /api/v1/tickets?folio= y respuesta de POST checkout — verificado contra TicketResponse. */
export interface TicketResponse {
  id: string;
  saleId: string;
  orderId: string;
  folio: string;
  items: TicketItemSnapshot[];
  subtotal: number;
  tip: number;
  tipPercent: number;
  total: number;
  paymentMethod: PaymentMethod;
  closedAt: string;
}

// ---------------------------------------------------------------------------
// Historial de ventas: verificado contra SaleHistoryRow, SaleHistorySummary y
// SalesHistoryResponse en backend/src/main/java/com/callejon9/sale/web/dto.
// ---------------------------------------------------------------------------

/**
 * Una fila del historial de ventas. `tableNumber`, `cashierName` y
 * `ticketId` pueden venir nulos (mesa eliminada, cajero dado de baja, o —
 * en teoria— una venta sin ticket todavia), asi que la pantalla debe
 * tolerarlos antes de mostrarlos o de armar un enlace al ticket.
 */
export interface SaleHistoryRow {
  id: string;
  orderFolio: string;
  tableNumber: number | null;
  cashierName: string | null;
  ticketId: string | null;
  paymentMethod: PaymentMethod;
  subtotal: number;
  tip: number;
  total: number;
  createdAt: string;
}

/** Numero de ventas y suma de sus totales para el rango solicitado, calculado por el servidor. */
export interface SaleHistorySummary {
  count: number;
  total: number;
}

/** GET /api/v1/sales — verificado contra SalesHistoryResponse. */
export interface SalesHistoryResponse {
  sales: SaleHistoryRow[];
  summary: SaleHistorySummary;
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

export const USER_ROLE_LABELS: Record<UserRole, string> = {
  SUPER_ADMIN: "Super administrador",
  ADMIN: "Administrador",
  WAITER: "Mesero",
  KITCHEN: "Cocina",
  CASHIER: "Cajero",
};
