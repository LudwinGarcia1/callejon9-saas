import type { ReactNode } from "react";

import { cn } from "@/lib/utils";

interface ScreenShellProps {
  /** Etiqueta mono en mayusculas sobre el titulo (folio, rango, contexto). */
  eyebrow?: ReactNode;
  title: ReactNode;
  subtitle?: ReactNode;
  /** Metricas, filtros o acciones alineados al pie del titulo. */
  actions?: ReactNode;
  children: ReactNode;
  className?: string;
  contentClassName?: string;
}

/**
 * Esqueleto comun de una pantalla: header con borde inferior y area de
 * contenido. Centraliza los paddings del sistema (26px/32px en escritorio,
 * 18px/20px en tablet, 14px/18px en movil) para que ninguna pantalla los
 * reinvente.
 */
export function ScreenShell({
  eyebrow,
  title,
  subtitle,
  actions,
  children,
  className,
  contentClassName,
}: ScreenShellProps) {
  return (
    <div className={cn("flex min-h-full flex-1 flex-col", className)}>
      <header className="flex flex-wrap items-end justify-between gap-x-6 gap-y-4 border-b px-[18px] pt-3.5 pb-4 sm:px-5 sm:pt-[18px] lg:px-8 lg:pt-[26px] lg:pb-5">
        <div className="min-w-0">
          {eyebrow && <p className="eyebrow">{eyebrow}</p>}
          <h1 className="font-display text-[28px] leading-[1.02] font-normal lg:text-[36px]">
            {title}
          </h1>
          {subtitle && (
            <p className="mt-1 text-sm text-muted-foreground text-pretty">{subtitle}</p>
          )}
        </div>
        {actions && <div className="flex flex-wrap items-end gap-x-[26px] gap-y-3">{actions}</div>}
      </header>

      <div
        className={cn(
          "flex-1 px-[18px] py-3.5 sm:px-5 sm:py-[18px] lg:px-8 lg:py-[26px]",
          contentClassName,
        )}
      >
        {children}
      </div>
    </div>
  );
}

interface ScreenMetricProps {
  label: string;
  value: ReactNode;
  /** La cuenta abierta del turno va en color de marca; el resto en tinta. */
  accent?: boolean;
}

/** Metrica del header: eyebrow arriba, cifra en display debajo, alineada a la derecha. */
export function ScreenMetric({ label, value, accent = false }: ScreenMetricProps) {
  return (
    <div className="text-right">
      <p className="eyebrow tracking-[0.1em]">{label}</p>
      <p
        className={cn(
          "mt-0.5 font-display text-[24px] leading-none lg:text-[28px]",
          accent && "text-brand",
        )}
      >
        {value}
      </p>
    </div>
  );
}
