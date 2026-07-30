/**
 * El backend serializa `BigDecimal` como numero JSON, asi que 499.00 llega
 * como 499. Estos formatters son solo para presentacion: nunca se debe hacer
 * aritmetica de dinero con estos valores, salvo una vista previa cosmetica
 * (por ejemplo una propina antes de confirmar el cobro) — el total
 * autoritativo siempre viene del servidor.
 */
const currencyFormatter = new Intl.NumberFormat("es-MX", {
  style: "currency",
  currency: "MXN",
});

const dateFormatter = new Intl.DateTimeFormat("es-MX", {
  day: "2-digit",
  month: "2-digit",
  year: "numeric",
});

const timeFormatter = new Intl.DateTimeFormat("es-MX", {
  hour: "2-digit",
  minute: "2-digit",
});

/** Formatea un monto como moneda mexicana, ej. `$499.00`. */
export function formatCurrency(amount: number): string {
  return currencyFormatter.format(amount);
}

/** Formatea una fecha ISO como fecha corta, ej. `28/07/2026`. */
export function formatShortDate(iso: string): string {
  return dateFormatter.format(new Date(iso));
}

/** Formatea una fecha ISO como hora corta, ej. `14:05`. */
export function formatShortTime(iso: string): string {
  return timeFormatter.format(new Date(iso));
}
