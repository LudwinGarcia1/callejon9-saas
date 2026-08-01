"use client";

import { useState } from "react";

import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { ChartTooltip } from "./chart-tooltip";
import { formatCurrency } from "@/lib/format";
import { PAYMENT_METHOD_LABELS, type PaymentMixRow } from "@/lib/types";

const VB_WIDTH = 640;
const ROW_HEIGHT = 40;
const MARGIN = { top: 12, right: 84, bottom: 12, left: 120 };
const PLOT_WIDTH = VB_WIDTH - MARGIN.left - MARGIN.right;

interface PaymentMixChartProps {
  data: PaymentMixRow[];
}

/** Mezcla de metodos de pago: barras horizontales, un solo matiz, con etiqueta directa -- nunca un pastel. */
export function PaymentMixChart({ data }: PaymentMixChartProps) {
  const [hovered, setHovered] = useState<number | null>(null);

  if (data.length === 0) {
    return (
      <p className="py-12 text-center text-sm text-muted-foreground">
        No hay ventas en el rango seleccionado.
      </p>
    );
  }

  const vbHeight = MARGIN.top + MARGIN.bottom + data.length * ROW_HEIGHT;
  const maxTotal = Math.max(...data.map((row) => row.total), 1);
  const xFor = (value: number) => MARGIN.left + (value / (maxTotal * 1.15)) * PLOT_WIDTH;

  // Ancho acotado: ver la nota en pareto-chart.tsx.
  return (
    <div className="relative w-full max-w-3xl">
      <svg
        viewBox={`0 0 ${VB_WIDTH} ${vbHeight}`}
        className="w-full"
        style={{ backgroundColor: "var(--surface)" }}
        role="img"
        aria-label="Ventas por metodo de pago"
      >
        <line
          x1={MARGIN.left}
          x2={MARGIN.left}
          y1={MARGIN.top}
          y2={vbHeight - MARGIN.bottom}
          stroke="var(--baseline)"
          strokeWidth={1}
        />

        {data.map((row, i) => {
          const rowTop = MARGIN.top + i * ROW_HEIGHT;
          const barHeight = ROW_HEIGHT - 8;
          const barY = rowTop + 4;
          const barLength = xFor(row.total) - MARGIN.left;
          const squareOverlayWidth = Math.min(4, barLength);
          const label = PAYMENT_METHOD_LABELS[row.method];

          return (
            <g key={row.method}>
              <rect
                x={MARGIN.left}
                y={barY}
                width={Math.max(barLength, 0)}
                height={barHeight}
                rx={4}
                ry={4}
                fill="var(--series-1)"
              />
              {barLength > 4 && (
                <rect x={MARGIN.left} y={barY} width={squareOverlayWidth} height={barHeight} fill="var(--series-1)" />
              )}
              <text
                x={MARGIN.left - 10}
                y={barY + barHeight / 2 + 3}
                textAnchor="end"
                fontSize={11}
                fill="var(--ink-primary)"
              >
                {label}
              </text>
              <text
                x={MARGIN.left + barLength + 8}
                y={barY + barHeight / 2 + 3}
                fontSize={10}
                fill="var(--ink-secondary)"
              >
                {formatCurrency(row.total)}
              </text>
              <rect
                x={0}
                y={rowTop}
                width={VB_WIDTH}
                height={ROW_HEIGHT}
                fill="transparent"
                tabIndex={0}
                role="button"
                aria-label={`${label}: ${formatCurrency(row.total)}, ${row.count} ventas, ${row.share.toFixed(1)}% del total`}
                onMouseEnter={() => setHovered(i)}
                onMouseLeave={() => setHovered(null)}
                onFocus={() => setHovered(i)}
                onBlur={() => setHovered(null)}
              />
            </g>
          );
        })}
      </svg>

      {hovered !== null && (
        <ChartTooltip
          xPercent={(xFor(data[hovered].total) / VB_WIDTH) * 100}
          yPercent={((MARGIN.top + hovered * ROW_HEIGHT + ROW_HEIGHT / 2) / vbHeight) * 100}
          visible
        >
          <p className="font-medium" style={{ color: "var(--ink-primary)" }}>
            {PAYMENT_METHOD_LABELS[data[hovered].method]}
          </p>
          <p style={{ color: "var(--ink-secondary)" }}>
            {formatCurrency(data[hovered].total)} · {data[hovered].count}{" "}
            {data[hovered].count === 1 ? "venta" : "ventas"} · {data[hovered].share.toFixed(1)}%
          </p>
        </ChartTooltip>
      )}
    </div>
  );
}

export function PaymentMixTable({ data }: PaymentMixChartProps) {
  if (data.length === 0) {
    return (
      <p className="py-12 text-center text-sm text-muted-foreground">
        No hay ventas en el rango seleccionado.
      </p>
    );
  }

  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>Metodo</TableHead>
          <TableHead>Ventas</TableHead>
          <TableHead>Total</TableHead>
          <TableHead>% del total</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {data.map((row) => (
          <TableRow key={row.method}>
            <TableCell>{PAYMENT_METHOD_LABELS[row.method]}</TableCell>
            <TableCell>{row.count}</TableCell>
            <TableCell>{formatCurrency(row.total)}</TableCell>
            <TableCell>{row.share.toFixed(1)}%</TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}
