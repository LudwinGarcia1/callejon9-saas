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
import { ChartEmpty } from "./chart-empty";
import { ChartLegend } from "./chart-legend";
import { ChartTooltip } from "./chart-tooltip";
import { formatCurrency } from "@/lib/format";
import type { ParetoRow } from "@/lib/types";

const VB_WIDTH = 640;
const VB_HEIGHT = 360;
const MARGIN = { top: 44, right: 16, bottom: 70, left: 40 };
const PLOT_WIDTH = VB_WIDTH - MARGIN.left - MARGIN.right;
const PLOT_HEIGHT = VB_HEIGHT - MARGIN.top - MARGIN.bottom;
const REFERENCE_LINE = 80;
const Y_TICKS = [0, 20, 40, 60, 100];

function truncate(label: string, max = 14): string {
  return label.length > max ? `${label.slice(0, max - 1)}…` : label;
}

function yFor(value: number): number {
  return MARGIN.top + PLOT_HEIGHT - (value / 100) * PLOT_HEIGHT;
}

interface ParetoChartProps {
  data: ParetoRow[];
}

/**
 * Diagrama de Pareto de productos: barras = participacion de ingresos, linea
 * = acumulado. Ambas series son porcentajes sobre el mismo eje 0-100 -- nunca
 * un eje secundario -- con una referencia punteada en 80%.
 */
export function ParetoChart({ data }: ParetoChartProps) {
  const [hovered, setHovered] = useState<number | null>(null);

  if (data.length === 0) {
    return (
      <ChartEmpty />
    );
  }

  const slot = PLOT_WIDTH / data.length;
  const barWidth = Math.min(slot * 0.7, 40);
  const plotBottom = MARGIN.top + PLOT_HEIGHT;

  const points = data.map((row, i) => ({
    cx: MARGIN.left + slot * i + slot / 2,
    cy: yFor(row.cumulativeShare),
  }));
  const linePath = points.map((p, i) => `${i === 0 ? "M" : "L"}${p.cx},${p.cy}`).join(" ");

  return (
    <div className="flex flex-col gap-3">
      <ChartLegend
        items={[
          { colorVar: "--series-1", label: "Participación de ingresos" },
          { colorVar: "--series-2", label: "Acumulado" },
        ]}
      />

      {/*
        El ancho se acota a proposito. Con `w-full` a secas el SVG se estira a
        todo el contenedor y, al conservar la relacion de aspecto del viewBox,
        la altura crecia hasta ~685px: una pantalla entera por grafica y una
        pagina de 2200px para tres graficas chicas. Acotar el contenedor (y no
        el SVG) mantiene correcto el calculo de posicion de los tooltips, que
        se mide contra este mismo elemento.
      */}
      <div className="relative w-full max-w-3xl">
        <svg
          viewBox={`0 0 ${VB_WIDTH} ${VB_HEIGHT}`}
          className="w-full"
          style={{ backgroundColor: "var(--surface)" }}
          role="img"
          aria-label="Diagrama de Pareto de productos por ingreso"
        >
          {Y_TICKS.map((tick) => (
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

          {/* Referencia 80%: punteada y distinta de las gridlines regulares. */}
          <line
            x1={MARGIN.left}
            x2={MARGIN.left + PLOT_WIDTH}
            y1={yFor(REFERENCE_LINE)}
            y2={yFor(REFERENCE_LINE)}
            stroke="var(--baseline)"
            strokeWidth={1}
            strokeDasharray="4 4"
          />
          <text
            x={MARGIN.left + PLOT_WIDTH}
            y={yFor(REFERENCE_LINE) - 5}
            textAnchor="end"
            fontSize={10}
            fill="var(--ink-muted)"
          >
            80%
          </text>

          {Y_TICKS.map((tick) => (
            <text
              key={tick}
              x={MARGIN.left - 8}
              y={yFor(tick) + 3}
              textAnchor="end"
              fontSize={9}
              fill="var(--ink-muted)"
            >
              {tick}
            </text>
          ))}

          {/* Eje base (0%). */}
          <line
            x1={MARGIN.left}
            x2={MARGIN.left + PLOT_WIDTH}
            y1={plotBottom}
            y2={plotBottom}
            stroke="var(--baseline)"
            strokeWidth={1}
          />

          {data.map((row, i) => {
            const barHeight = plotBottom - yFor(row.revenueShare);
            const x = MARGIN.left + slot * i + (slot - barWidth) / 2;
            const y = yFor(row.revenueShare);
            const squareOverlayHeight = Math.min(4, barHeight);
            return (
              <g key={row.productName}>
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
                <text
                  x={MARGIN.left + slot * i + slot / 2}
                  y={plotBottom + 14}
                  textAnchor="end"
                  fontSize={9}
                  fill="var(--ink-muted)"
                  transform={`rotate(-35 ${MARGIN.left + slot * i + slot / 2} ${plotBottom + 14})`}
                >
                  {truncate(row.productName)}
                </text>
                {/* Zona de hover mas grande que la barra visible. */}
                <rect
                  x={MARGIN.left + slot * i}
                  y={MARGIN.top}
                  width={slot}
                  height={PLOT_HEIGHT}
                  fill="transparent"
                  tabIndex={0}
                  role="button"
                  aria-label={`${row.productName}: ${formatCurrency(row.revenue)}, ${row.revenueShare.toFixed(1)}% de ingresos, ${row.cumulativeShare.toFixed(1)}% acumulado`}
                  onMouseEnter={() => setHovered(i)}
                  onMouseLeave={() => setHovered(null)}
                  onFocus={() => setHovered(i)}
                  onBlur={() => setHovered(null)}
                />
              </g>
            );
          })}

          <path d={linePath} fill="none" stroke="var(--series-2)" strokeWidth={2} />
          {points.map((p, i) => (
            <circle
              key={data[i].productName}
              cx={p.cx}
              cy={p.cy}
              r={4}
              fill="var(--series-2)"
              stroke="var(--surface)"
              strokeWidth={2}
            />
          ))}
        </svg>

        {hovered !== null && (
          <ChartTooltip
            xPercent={((MARGIN.left + slot * hovered + slot / 2) / VB_WIDTH) * 100}
            yPercent={(yFor(data[hovered].revenueShare) / VB_HEIGHT) * 100}
            visible
          >
            <p className="font-medium" style={{ color: "var(--ink-primary)" }}>
              {data[hovered].productName}
            </p>
            <p style={{ color: "var(--ink-secondary)" }}>{formatCurrency(data[hovered].revenue)}</p>
            <p style={{ color: "var(--ink-secondary)" }}>
              {data[hovered].revenueShare.toFixed(1)}% de ingresos · {data[hovered].cumulativeShare.toFixed(1)}% acumulado
            </p>
          </ChartTooltip>
        )}
      </div>
    </div>
  );
}

/** Tabla equivalente a la grafica: misma informacion, sin depender del color. */
export function ParetoTable({ data }: ParetoChartProps) {
  if (data.length === 0) {
    return (
      <ChartEmpty />
    );
  }

  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>Producto</TableHead>
          <TableHead>Ingreso</TableHead>
          <TableHead>% de ingresos</TableHead>
          <TableHead>% acumulado</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {data.map((row) => (
          <TableRow key={row.productName}>
            <TableCell>{row.productName}</TableCell>
            <TableCell className="font-mono text-[13px] tabular-nums">
              {formatCurrency(row.revenue)}
            </TableCell>
            <TableCell className="font-mono text-[13px] tabular-nums">
              {row.revenueShare.toFixed(1)}%
            </TableCell>
            <TableCell className="font-mono text-[13px] tabular-nums">
              {row.cumulativeShare.toFixed(1)}%
            </TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}
