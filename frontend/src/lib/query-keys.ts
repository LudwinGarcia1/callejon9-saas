/**
 * Factory de query keys tipada. Mantener las claves centralizadas evita que
 * el hook que dispara una consulta y el que la invalida terminen con claves
 * distintas por error de tipeo.
 */
export const queryKeys = {
  session: {
    me: () => ["session", "me"] as const,
  },
  tables: {
    all: () => ["tables"] as const,
    detail: (tableId: string) => ["tables", tableId] as const,
  },
  categories: {
    all: () => ["categories"] as const,
  },
  products: {
    all: () => ["products"] as const,
    detail: (productId: string) => ["products", productId] as const,
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
