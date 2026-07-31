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
    all: () => ["orders"] as const,
    detail: (orderId: string) => ["orders", orderId] as const,
  },
  kitchen: {
    orders: () => ["kitchen", "orders"] as const,
  },
  tickets: {
    detail: (ticketId: string) => ["tickets", ticketId] as const,
  },
};
