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
import { formatCurrency, formatIsoDate } from "@/lib/format";
import type { SalesByDayRow } from "@/lib/types";

const VB_WIDTH = 640;
const VB_HEIGHT = 280;
const MARGIN = { top: 24, right: 16, bottom: 46, left: 64 };
const PLOT_WIDTH = VB_WIDTH - MARGIN.left - MARGIN.right;
const PLOT_HEIGHT = VB_HEIGHT - MARGIN.top - MARGIN.bottom;

interface DailySalesChartProps {
  data: SalesByDayRow[];
}

function hasNoSales(data: SalesByDayRow[]): boolean {
  return data.length === 0 || data.every((row) => row.count === 0);
}

/** Ventas por dia: una sola serie, sin leyenda -- el titulo de la tarjeta ya la nombra. */
export function DailySalesChart({ data }: DailySalesChartProps) {
  const [hovered, setHovered] = useState<number | null>(null);

  if (hasNoSales(data)) {
    return (
      <p className="py-12 text-center text-sm text-muted-foreground">
        No hay ventas en el rango seleccionado.
      </p>
    );
  }

  const maxTotal = Math.max(...data.map((row) => row.total), 0);
  const scaleMax = maxTotal * 1.1 || 1;
  const plotBottom = MARGIN.top + PLOT_HEIGHT;
  const slot = PLOT_WIDTH / data.length;
  const barWidth = Math.min(slot * 0.6, 48);

  const yFor = (value: number) => MARGIN.top + PLOT_HEIGHT - (value / scaleMax) * PLOT_HEIGHT;
  const maxIndex = data.reduce((best, row, i) => (row.total > data[best].total ? i : best), 0);

  // Ancho acotado: ver la nota en pareto-chart.tsx.
  return (
    <div className="relative w-full max-w-3xl">
      <svg
        viewBox={`0 0 ${VB_WIDTH} ${VB_HEIGHT}`}
        className="w-full"
        style={{ backgroundColor: "var(--surface)" }}
        role="img"
        aria-label="Ventas por dia"
      >
        {[0, scaleMax / 2, scaleMax].map((tick) => (
          <line
            key={tick}
            x1={MARGIN.left}
            x2={MARGIN.left + PLOT_WIDTH}
            y1={yFor(tick)}
            y2={yFor(tick)}
            stroke="var(--gridline)"
            strokeWidth={1}
          />
        ))}
        {[0, scaleMax / 2, scaleMax].map((tick) => (
          <text
            key={tick}
            x={MARGIN.left - 8}
            y={yFor(tick) + 3}
            textAnchor="end"
            fontSize={9}
            fill="var(--ink-muted)"
          >
            {formatCurrency(tick)}
          </text>
        ))}

        <line
          x1={MARGIN.left}
          x2={MARGIN.left + PLOT_WIDTH}
          y1={plotBottom}
          y2={plotBottom}
          stroke="var(--baseline)"
          strokeWidth={1}
        />

        {data.map((row, i) => {
          const barHeight = plotBottom - yFor(row.total);
          const x = MARGIN.left + slot * i + (slot - barWidth) / 2;
          const y = yFor(row.total);
          const squareOverlayHeight = Math.min(4, barHeight);
          return (
            <g key={row.day}>
              <rect x={x} y={y} width={barWidth} height={barHeight} rx={4} ry={4} fill="var(--series-1)" />
              {barHeight > 4 && (
                <rect
                  x={x}
                  y={plotBottom - squareOverlayHeight}
                  width={barWidth}
                  height={squareOverlayHeight}
                  fill="var(--series-1)"
                />
              )}
              {/* Etiqueta directa selectiva: solo el dia de mayor venta, nunca todas las barras. */}
              {i === maxIndex && row.total > 0 && (
                <text
                  x={x + barWidth / 2}
                  y={y - 6}
                  textAnchor="middle"
                  fontSize={9}
                  fill="var(--ink-secondary)"
                >
                  {formatCurrency(row.total)}
                </text>
              )}
              <text
                x={MARGIN.left + slot * i + slot / 2}
                y={plotBottom + 16}
                textAnchor="middle"
                fontSize={9}
                fill="var(--ink-muted)"
              >
                {formatIsoDate(row.day)}
              </text>
              <rect
                x={MARGIN.left + slot * i}
                y={MARGIN.top}
                width={slot}
                height={PLOT_HEIGHT}
                fill="transparent"
                tabIndex={0}
                role="button"
                aria-label={`${formatIsoDate(row.day)}: ${formatCurrency(row.total)}, ${row.count} ventas`}
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
          xPercent={((MARGIN.left + slot * hovered + slot / 2) / VB_WIDTH) * 100}
          yPercent={(yFor(data[hovered].total) / VB_HEIGHT) * 100}
          visible
        >
          <p className="font-medium" style={{ color: "var(--ink-primary)" }}>
            {formatIsoDate(data[hovered].day)}
          </p>
          <p style={{ color: "var(--ink-secondary)" }}>
            {formatCurrency(data[hovered].total)} · {data[hovered].count}{" "}
            {data[hovered].count === 1 ? "venta" : "ventas"}
          </p>
        </ChartTooltip>
      )}
    </div>
  );
}

export function DailySalesTable({ data }: DailySalesChartProps) {
  if (hasNoSales(data)) {
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
          <TableHead>Dia</TableHead>
          <TableHead>Ventas</TableHead>
          <TableHead>Total</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {data.map((row) => (
          <TableRow key={row.day}>
            <TableCell>{formatIsoDate(row.day)}</TableCell>
            <TableCell>{row.count}</TableCell>
            <TableCell>{formatCurrency(row.total)}</TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}
