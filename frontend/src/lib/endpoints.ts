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
    /** PUT: corrige numero/capacidad. PATCH: da de alta o de baja. Mismo path. */
    update: (tableId: string) => `/api/v1/tables/${tableId}`,
    updateStatus: (tableId: string) => `/api/v1/tables/${tableId}`,
  },
  categories: {
    list: () => "/api/v1/categories",
    create: () => "/api/v1/categories",
    update: (categoryId: string) => `/api/v1/categories/${categoryId}`,
  },
  products: {
    list: () => "/api/v1/products",
    create: () => "/api/v1/products",
    /** PUT: corrige nombre/descripcion/precio/categoria. PATCH: alta o baja. */
    update: (productId: string) => `/api/v1/products/${productId}`,
    updateStatus: (productId: string) => `/api/v1/products/${productId}`,
  },
  users: {
    list: () => "/api/v1/users",
    create: () => "/api/v1/users",
    updateStatus: (userId: string) => `/api/v1/users/${userId}`,
  },
  orders: {
    list: () => "/api/v1/orders",
    open: () => "/api/v1/orders",
    detail: (orderId: string) => `/api/v1/orders/${orderId}`,
    items: (orderId: string) => `/api/v1/orders/${orderId}/items`,
    sendToKitchen: (orderId: string) => `/api/v1/orders/${orderId}/send-to-kitchen`,
    checkout: (orderId: string) => `/api/v1/orders/${orderId}/checkout`,
    cancel: (orderId: string) => `/api/v1/orders/${orderId}/cancel`,
  },
  kitchen: {
    orders: () => "/api/v1/kitchen/orders",
    itemStatus: (itemId: string) => `/api/v1/kitchen/items/${itemId}/status`,
  },
  tickets: {
    detail: (ticketId: string) => `/api/v1/tickets/${ticketId}`,
    pdf: (ticketId: string) => `/api/v1/tickets/${ticketId}/pdf`,
    /** El folio va como query param (?folio=...), no como segmento de ruta. */
    byFolio: () => "/api/v1/tickets",
  },
  sales: {
    /** GET; acepta los query params opcionales `from`/`to` (fechas ISO). */
    history: () => "/api/v1/sales",
  },
  analytics: {
    /** GET; acepta los query params opcionales `from`/`to` (fechas ISO). Default: ultimos 7 dias. */
    summary: () => "/api/v1/analytics",
  },
  inventory: {
    items: () => "/api/v1/inventory/items",
    createItem: () => "/api/v1/inventory/items",
    /** PUT: corrige nombre/unidad/minimo/costo. PATCH: alta o baja. Mismo path. */
    updateItem: (itemId: string) => `/api/v1/inventory/items/${itemId}`,
    updateItemStatus: (itemId: string) => `/api/v1/inventory/items/${itemId}`,
    /** GET; acepta `from`/`to` (fechas ISO) e `itemId`. Default: hoy. */
    movements: () => "/api/v1/inventory/movements",
    registerMovement: () => "/api/v1/inventory/movements",
  },
};
