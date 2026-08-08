import { Badge } from "@/components/ui/badge";
import {
  KITCHEN_STATUS_LABELS,
  MOVEMENT_TYPE_LABELS,
  ORDER_STATUS_LABELS,
  PAYMENT_METHOD_LABELS,
  STOCK_LEVEL_LABELS,
  TABLE_STATUS_LABELS,
  type InventoryMovementType,
  type KitchenItemStatus,
  type OrderStatus,
  type PaymentMethod,
  type StockLevel,
  type TableStatus,
} from "@/lib/types";

type StatusBadgeProps =
  | { kind: "order"; status: OrderStatus }
  | { kind: "table"; status: TableStatus }
  | { kind: "kitchen"; status: KitchenItemStatus }
  | { kind: "payment"; status: PaymentMethod }
  | { kind: "stock"; status: StockLevel }
  | { kind: "movement"; status: InventoryMovementType };

/**
 * Badge con la etiqueta en espanol de cualquiera de las seis familias de
 * estado del dominio. `src/lib/types.ts` es la unica fuente de esas
 * etiquetas; este componente solo elige el mapa correcto segun `kind`.
 *
 * El nivel de stock es la unica familia que cambia de color: un stock negativo
 * no es un estado mas, es la senal de que el conteo fisico esta mal, y en una
 * lista de treinta insumos una insignia gris se pierde.
 */
export function StatusBadge(props: StatusBadgeProps) {
  return <Badge variant={variantFor(props)}>{labelFor(props)}</Badge>;
}

function variantFor(props: StatusBadgeProps): "secondary" | "destructive" | "outline" {
  if (props.kind === "stock") {
    if (props.status === "NEGATIVE") {
      return "destructive";
    }
    if (props.status === "LOW") {
      return "outline";
    }
  }
  return "secondary";
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
    case "stock":
      return STOCK_LEVEL_LABELS[props.status];
    case "movement":
      return MOVEMENT_TYPE_LABELS[props.status];
  }
}
