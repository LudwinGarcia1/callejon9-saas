import { Badge } from "@/components/ui/badge";
import {
  KITCHEN_STATUS_LABELS,
  ORDER_STATUS_LABELS,
  PAYMENT_METHOD_LABELS,
  TABLE_STATUS_LABELS,
  type KitchenItemStatus,
  type OrderStatus,
  type PaymentMethod,
  type TableStatus,
} from "@/lib/types";

type StatusBadgeProps =
  | { kind: "order"; status: OrderStatus }
  | { kind: "table"; status: TableStatus }
  | { kind: "kitchen"; status: KitchenItemStatus }
  | { kind: "payment"; status: PaymentMethod };

/**
 * Badge con la etiqueta en espanol de cualquiera de las cuatro familias de
 * estado del dominio. `src/lib/types.ts` es la unica fuente de esas
 * etiquetas; este componente solo elige el mapa correcto segun `kind`.
 */
export function StatusBadge(props: StatusBadgeProps) {
  return <Badge variant="secondary">{labelFor(props)}</Badge>;
}

function labelFor(props: StatusBadgeProps): string {
  switch (props.kind) {
    case "order":
      return ORDER_STATUS_LABELS[props.status];
    case "table":
      return TABLE_STATUS_LABELS[props.status];
    case "kitchen":
      return KITCHEN_STATUS_LABELS[props.status];
    case "payment":
      return PAYMENT_METHOD_LABELS[props.status];
  }
}
