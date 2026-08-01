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

/** Fecha de hoy en formato `yyyy-MM-dd`, para precargar un input `type="date"`. */
export function todayIsoDate(): string {
  return new Date().toISOString().slice(0, 10);
}

/**
 * Formatea una fecha ISO *sin hora* (`yyyy-MM-dd`, ej. la de un dia de
 * `salesByDay`) como fecha corta, ej. `20/07/2026`.
 *
 * A diferencia de {@link formatShortDate}, arma la fecha con sus componentes
 * en vez de dejar que `new Date(iso)` la interprete como un instante UTC: si
 * se hiciera asi, en una zona detras de UTC (Mexico, UTC-6) la fecha se veria
 * corrida un dia hacia atras.
 */
export function formatIsoDate(isoDate: string): string {
  const [year, month, day] = isoDate.split("-").map(Number);
  return dateFormatter.format(new Date(year, month - 1, day));
}
