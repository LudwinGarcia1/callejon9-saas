import { formatCurrency } from "@/lib/format";
import { cn } from "@/lib/utils";

interface MoneyProps {
  amount: number;
  className?: string;
}

/**
 * Muestra un monto en pesos mexicanos. El numero que llega del backend es un
 * `BigDecimal` serializado como numero JSON (499.00 llega como 499); este
 * componente solo lo formatea, nunca opera aritmeticamente sobre el.
 *
 * Siempre en cifras tabulares: una columna de montos tiene que alinearse sola.
 */
export function Money({ amount, className }: MoneyProps) {
  return <span className={cn("tabular-nums", className)}>{formatCurrency(amount)}</span>;
}
