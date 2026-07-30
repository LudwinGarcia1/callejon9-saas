/**
 * Constructores de ruta para cada endpoint del backend, agrupados por
 * funcionalidad. Este es el unico archivo que hay que corregir si una ruta
 * termina en un lugar distinto al esperado.
 */
export const endpoints = {
  auth: {
    signup: () => "/api/v1/signup",
    login: () => "/api/v1/auth/login",
    logout: () => "/api/v1/auth/logout",
    /** No verificado: GET /api/v1/auth/me todavia no existe en el backend. */
    me: () => "/api/v1/auth/me",
  },
  platform: {
    plans: () => "/api/v1/platform/plans",
  },
  tables: {
    list: () => "/api/v1/tables",
    create: () => "/api/v1/tables",
    detail: (tableId: string) => `/api/v1/tables/${tableId}`,
  },
  categories: {
    list: () => "/api/v1/categories",
    create: () => "/api/v1/categories",
    detail: (categoryId: string) => `/api/v1/categories/${categoryId}`,
  },
  products: {
    list: () => "/api/v1/products",
    create: () => "/api/v1/products",
    detail: (productId: string) => `/api/v1/products/${productId}`,
  },
  orders: {
    list: () => "/api/v1/orders",
    open: () => "/api/v1/orders",
    detail: (orderId: string) => `/api/v1/orders/${orderId}`,
    items: (orderId: string) => `/api/v1/orders/${orderId}/items`,
    /** No verificado contra el backend. */
    checkout: (orderId: string) => `/api/v1/orders/${orderId}/checkout`,
  },
  /** No verificado contra el backend: workstream de cocina en construccion. */
  kitchen: {
    orders: () => "/api/v1/kitchen/orders",
    itemStatus: (itemId: string) => `/api/v1/kitchen/items/${itemId}/status`,
  },
  /** No verificado contra el backend: workstream de tickets en construccion. */
  tickets: {
    detail: (ticketId: string) => `/api/v1/tickets/${ticketId}`,
    pdf: (ticketId: string) => `/api/v1/tickets/${ticketId}/pdf`,
  },
};
