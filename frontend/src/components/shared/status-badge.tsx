import { Badge } from "@/components/ui/badge";
import { cn } from "@/lib/utils";
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
 * Las cuatro familias de estado del dominio se reducen a cuatro tonos del
 * sistema: verde (libre o lista), marca (activa u ocupada), ambar (esperando
 * algo) y neutro (fuera de juego). Un tono se publica como `data-tone` y de
 * ahi lo leen el chip, el punto y cualquier texto que deba ir en su color.
 */
export type StatusTone = "green" | "brand" | "amber" | "neutral";

const ORDER_TONES: Record<OrderStatus, StatusTone> = {
  NEW: "green",
  SENT: "amber",
  READY: "green",
  PAID: "neutral",
  CANCELED: "neutral",
};

const TABLE_TONES: Record<TableStatus, StatusTone> = {
  FREE: "green",
  OCCUPIED: "brand",
  RESERVED: "amber",
  CLEANING: "neutral",
};

const KITCHEN_TONES: Record<KitchenItemStatus, StatusTone> = {
  PENDING: "amber",
  IN_PREPARATION: "brand",
  READY: "green",
  DELIVERED: "neutral",
};

/** Tono del sistema para cualquiera de las cuatro familias de estado. */
export function statusTone(props: StatusBadgeProps): StatusTone {
  switch (props.kind) {
    case "order":
      return ORDER_TONES[props.status];
    case "table":
      return TABLE_TONES[props.status];
    case "kitchen":
      return KITCHEN_TONES[props.status];
    case "payment":
      return "neutral";
  }
}

/** Etiqueta en espanol. `src/lib/types.ts` es la unica fuente de estos textos. */
export function statusLabel(props: StatusBadgeProps): string {
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

/** Chip de estado: borde de 1px sobre fondo tinte, en el tono de su estado. */
export function StatusBadge(props: StatusBadgeProps & { className?: string }) {
  const { className, ...status } = props;
  return (
    <Badge data-tone={statusTone(status)} className={className}>
      {statusLabel(status)}
    </Badge>
  );
}

/**
 * Punto de 9px del color del estado. Es la marca de estado de la tarjeta de
 * mesa en escritorio, donde el chip completo competiria con el numero.
 */
export function StatusDot({ className }: { className?: string }) {
  return (
    <span
      aria-hidden
      className={cn("mt-1.5 block size-[9px] shrink-0 rounded-full bg-[var(--tone)]", className)}
    />
  );
}
