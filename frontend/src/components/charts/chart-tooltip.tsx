"use client";

interface ChartTooltipProps {
  /** Posicion horizontal, en porcentaje (0-100) del contenedor relativo. */
  xPercent: number;
  /** Posicion vertical, en porcentaje (0-100) del contenedor relativo. */
  yPercent: number;
  visible: boolean;
  children: React.ReactNode;
}

/**
 * Tooltip flotante para un punto de datos, posicionado por porcentaje sobre
 * el contenedor relativo que envuelve el SVG (que a su vez preserva su
 * relacion de aspecto con `width: 100%`), asi que el porcentaje del viewBox
 * coincide con el porcentaje del contenedor sin medir nada en JavaScript.
 */
export function ChartTooltip({ xPercent, yPercent, visible, children }: ChartTooltipProps) {
  if (!visible) {
    return null;
  }

  return (
    <div
      role="tooltip"
      className="pointer-events-none absolute z-10 -translate-x-1/2 -translate-y-[calc(100%+8px)] rounded-md border bg-popover px-2.5 py-1.5 text-xs whitespace-nowrap text-popover-foreground shadow-md"
      style={{ left: `${xPercent}%`, top: `${yPercent}%` }}
    >
      {children}
    </div>
  );
}
