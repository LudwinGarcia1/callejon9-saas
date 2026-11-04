/**
 * Factory de query keys tipada. Mantener las claves centralizadas evita que
 * el hook que dispara una consulta y el que la invalida terminen con claves
 * distintas por error de tipeo.
 */
export const queryKeys = {
  session: {
    me: () => ["session", "me"] as const,
  },
  platform: {
    plans: () => ["platform", "plans"] as const,
  },
  settings: {
    detail: () => ["settings"] as const,
  },
  tables: {
    /** Sin argumento: solo mesas activas (default del backend). Con
     * `includeInactive = true`: la clave usada por la pantalla de
     * administracion, que necesita ver tambien las dadas de baja. */
    all: (includeInactive?: boolean) =>
      includeInactive ? (["tables", { includeInactive: true }] as const) : (["tables"] as const),
    detail: (tableId: string) => ["tables", tableId] as const,
  },
  categories: {
    all: () => ["categories"] as const,
  },
  products: {
    /** Mismo criterio que {@link tables.all}: la administracion pide
     * `includeInactive = true` para poder reactivar productos dados de baja. */
    all: (includeInactive?: boolean) =>
      includeInactive
        ? (["products", { includeInactive: true }] as const)
        : (["products"] as const),
    detail: (productId: string) => ["products", productId] as const,
  },
  users: {
    all: () => ["users"] as const,
  },
  orders: {
    /** Prefijo de todo lo de ordenes: invalidarlo refresca abiertas y detalles. */
    all: () => ["orders"] as const,
    /** Pagina de ordenes en servicio que comparten mesero, caja y navegacion. */
    open: () => ["orders", { open: true }] as const,
    detail: (orderId: string) => ["orders", orderId] as const,
  },
  kitchen: {
    orders: () => ["kitchen", "orders"] as const,
  },
  tickets: {
    detail: (ticketId: string) => ["tickets", ticketId] as const,
  },
  sales: {
    all: () => ["sales"] as const,
    /** El rango y la pagina entran en la clave: cambiar cualquiera dispara otra consulta. */
    history: (from: string, to: string, page: number, size: number) =>
      ["sales", { from, to, page, size }] as const,
  },
  cashSessions: {
    all: () => ["cash-sessions"] as const,
    detail: (sessionId: string) => ["cash-sessions", sessionId] as const,
  },
  analytics: {
    all: () => ["analytics"] as const,
    /** Igual criterio que `sales.history`: el rango es parte de la clave. */
    summary: (from: string, to: string) => ["analytics", { from, to }] as const,
  },
  inventory: {
    /** Mismo criterio que {@link products.all}: la pantalla pide
     * `includeInactive` para poder reactivar insumos dados de baja. */
    items: (includeInactive?: boolean) =>
      includeInactive
        ? (["inventory", "items", { includeInactive: true }] as const)
        : (["inventory", "items"] as const),
    /** Prefijo de todos los rangos de movimientos, para invalidarlos juntos. */
    allMovements: () => ["inventory", "movements"] as const,
    /** El rango y el filtro de insumo son parte de la clave. */
    movements: (from: string, to: string, itemId?: string) =>
      ["inventory", "movements", { from, to, itemId: itemId ?? null }] as const,
  },
};
