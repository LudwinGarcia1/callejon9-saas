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
  // El backend no expone GET por id para mesas, categorias ni productos:
  // esas colecciones se leen completas y se filtran en el cliente.
  tables: {
    list: () => "/api/v1/tables",
    create: () => "/api/v1/tables",
  },
  categories: {
    list: () => "/api/v1/categories",
    create: () => "/api/v1/categories",
  },
  products: {
    list: () => "/api/v1/products",
    create: () => "/api/v1/products",
  },
  orders: {
    list: () => "/api/v1/orders",
    open: () => "/api/v1/orders",
    detail: (orderId: string) => `/api/v1/orders/${orderId}`,
    items: (orderId: string) => `/api/v1/orders/${orderId}/items`,
    sendToKitchen: (orderId: string) => `/api/v1/orders/${orderId}/send-to-kitchen`,
    checkout: (orderId: string) => `/api/v1/orders/${orderId}/checkout`,
  },
  kitchen: {
    orders: () => "/api/v1/kitchen/orders",
    itemStatus: (itemId: string) => `/api/v1/kitchen/items/${itemId}/status`,
  },
  tickets: {
    detail: (ticketId: string) => `/api/v1/tickets/${ticketId}`,
    pdf: (ticketId: string) => `/api/v1/tickets/${ticketId}/pdf`,
  },
};
