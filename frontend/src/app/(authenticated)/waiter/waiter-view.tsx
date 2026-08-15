"use client";

import { useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import { toast } from "sonner";

import { Skeleton } from "@/components/ui/skeleton";
import { ScreenMetric, ScreenShell } from "@/components/layout/screen-shell";
import { Money } from "@/components/shared/money";
import { QueryState } from "@/components/shared/query-state";
import { StatusDot, statusTone } from "@/components/shared/status-badge";
import { api } from "@/lib/api";
import { endpoints } from "@/lib/endpoints";
import { formatShortTime } from "@/lib/format";
import { queryKeys } from "@/lib/query-keys";
import { cn } from "@/lib/utils";
import {
  TABLE_STATUS_LABELS,
  type OrderSummaryResponse,
  type TableResponse,
  type TableStatus,
} from "@/lib/types";
import { OpenTableDialog } from "./open-table-dialog";

/** Cada tantos milisegundos se refresca la lista de mesas, para que una mesa
 * liberada por caja aparezca sin que el mesero tenga que recargar. */
const TABLES_POLL_INTERVAL_MS = 10_000;

/** Estados de orden que cuentan como "todavia abierta" al buscar la orden de
 * una mesa ocupada. */
const OPEN_ORDER_STATUSES = new Set(["NEW", "SENT", "READY"]);

type TableFilter = "ALL" | TableStatus;

const FILTERS: { value: TableFilter; label: string }[] = [
  { value: "ALL", label: "Todas" },
  { value: "FREE", label: "Libres" },
  { value: "OCCUPIED", label: "Ocupadas" },
  { value: "RESERVED", label: "Reservadas" },
];

/** Texto de la accion principal de cada estado, el que va en el CTA de tablet. */
const TABLE_CTA: Record<TableStatus, string> = {
  FREE: "Abrir mesa",
  OCCUPIED: "Ver comanda",
  RESERVED: "Reservada",
  CLEANING: "En limpieza",
};

/**
 * Cuadricula de mesas para el mesero. Tocar una mesa libre abre el dialogo
 * para capturar comensales; tocar una ocupada navega a su orden abierta (no
 * existe un endpoint "orden de esta mesa", asi que se resuelve contra el
 * listado de ordenes que esta pantalla ya necesita para los montos).
 *
 * Por debajo de 1280px la tarjeta deja de ser clicable completa y la accion
 * pasa a un CTA de 52px: en tablet, tocar una tarjeta entera de 190px de alto
 * provoca demasiados toques por error.
 */
export function WaiterView() {
  const router = useRouter();
  const [selectedFreeTable, setSelectedFreeTable] = useState<TableResponse | null>(null);
  const [filter, setFilter] = useState<TableFilter>("ALL");

  const tablesQuery = useQuery({
    queryKey: queryKeys.tables.all(),
    queryFn: () => api.get<TableResponse[]>(endpoints.tables.list()),
    refetchInterval: TABLES_POLL_INTERVAL_MS,
  });

  const ordersQuery = useQuery({
    queryKey: queryKeys.orders.all(),
    queryFn: () => api.get<OrderSummaryResponse[]>(endpoints.orders.list()),
    refetchInterval: TABLES_POLL_INTERVAL_MS,
  });

  /** Orden abierta de cada mesa, para el monto de la tarjeta y para navegar. */
  const openOrderByTable = useMemo(() => {
    const index = new Map<string, OrderSummaryResponse>();
    for (const order of ordersQuery.data ?? []) {
      if (order.tableId && OPEN_ORDER_STATUSES.has(order.status)) {
        index.set(order.tableId, order);
      }
    }
    return index;
  }, [ordersQuery.data]);

  const tables = tablesQuery.data ?? [];
  const occupiedCount = tables.filter((table) => table.status === "OCCUPIED").length;
  const openTotal = [...openOrderByTable.values()].reduce((sum, order) => sum + order.total, 0);
  const visibleTables =
    filter === "ALL" ? tables : tables.filter((table) => table.status === filter);

  function handleTableClick(table: TableResponse) {
    if (table.status === "FREE") {
      setSelectedFreeTable(table);
      return;
    }

    if (table.status !== "OCCUPIED") {
      return;
    }

    const openOrder = openOrderByTable.get(table.id);
    if (!openOrder) {
      toast.error(`No se encontró una orden abierta para la mesa ${table.number}.`);
      return;
    }

    router.push(`/waiter/order/${openOrder.id}`);
  }

  return (
    <ScreenShell
      title="Mesas"
      subtitle="Toca una mesa libre para abrir una orden, o una ocupada para continuarla."
      actions={
        <>
          <ScreenMetric label="Ocupación" value={`${occupiedCount} / ${tables.length}`} />
          <ScreenMetric label="Cuenta abierta" value={<Money amount={openTotal} />} accent />
          <div className="flex gap-1.5">
            {FILTERS.map((option) => (
              <button
                key={option.value}
                type="button"
                onClick={() => setFilter(option.value)}
                aria-pressed={filter === option.value}
                className={cn(
                  "focus-sala inline-flex h-11 items-center rounded-sm border px-3 font-mono text-[11px] tracking-[0.06em] uppercase lg:h-8",
                  filter === option.value
                    ? "border-primary bg-primary text-primary-foreground"
                    : "border-border text-muted-foreground hover:text-foreground",
                )}
              >
                {option.label}
              </button>
            ))}
          </div>
        </>
      }
    >
      <QueryState
        isLoading={tablesQuery.isLoading}
        error={tablesQuery.error}
        isEmpty={tables.length === 0}
        emptyMessage="Todavía no hay mesas registradas."
        skeleton={<TableGridSkeleton />}
      >
        {visibleTables.length === 0 ? (
          <div className="py-6">
            <p className="eyebrow">Sin mesas</p>
            <p className="mt-1 text-sm text-muted-foreground">
              Ninguna mesa coincide con este filtro.
            </p>
          </div>
        ) : (
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
            {visibleTables.map((table) => (
              <TableCard
                key={table.id}
                table={table}
                order={openOrderByTable.get(table.id) ?? null}
                onSelect={() => handleTableClick(table)}
              />
            ))}
          </div>
        )}
      </QueryState>

      <OpenTableDialog
        table={selectedFreeTable}
        onOpenChange={(open) => {
          if (!open) {
            setSelectedFreeTable(null);
          }
        }}
      />
    </ScreenShell>
  );
}

interface TableCardProps {
  table: TableResponse;
  order: OrderSummaryResponse | null;
  onSelect: () => void;
}

function TableCard({ table, order, onSelect }: TableCardProps) {
  const tone = statusTone({ kind: "table", status: table.status });
  const isActionable = table.status === "FREE" || table.status === "OCCUPIED";
  const isOccupied = table.status === "OCCUPIED";

  const meta =
    isOccupied && order
      ? `${formatShortTime(order.openedAt)} · ${order.folio}`
      : table.status === "FREE"
        ? "Lista para sentar"
        : table.status === "RESERVED"
          ? "Apartada para el turno"
          : "Pendiente de limpieza";

  return (
    <div
      data-tone={tone}
      role={isActionable ? "button" : undefined}
      tabIndex={isActionable ? 0 : undefined}
      onClick={isActionable ? onSelect : undefined}
      onKeyDown={
        isActionable
          ? (event) => {
              if (event.key === "Enter" || event.key === " ") {
                event.preventDefault();
                onSelect();
              }
            }
          : undefined
      }
      className={cn(
        "focus-sala flex min-h-[190px] flex-col justify-between rounded-xl border p-[18px] outline-none",
        isOccupied ? "border-brand-tint-border bg-brand-tint" : "border-border bg-card",
        isActionable ? "cursor-pointer hover:border-border-strong" : "opacity-80",
      )}
    >
      <div className="flex items-start justify-between">
        <div>
          <p className="eyebrow">Mesa</p>
          <p className="font-display text-[46px] leading-[0.95] tabular-nums">{table.number}</p>
        </div>
        <StatusDot />
      </div>

      <div className="flex flex-col gap-[7px]">
        <p className="font-mono text-[11px] tracking-[0.1em] text-[var(--tone)] uppercase">
          {TABLE_STATUS_LABELS[table.status]}
        </p>
        <div className="h-px bg-border" />
        <div className="flex items-baseline justify-between gap-2">
          <span className="text-[13px] text-muted-foreground">{table.capacity} lugares</span>
          {/* Una mesa sin cuenta muestra un guion, no $0.00: cero pesos es un
              dato; "todavia no hay cuenta" es otro. */}
          {order ? (
            <Money amount={order.total} className="font-display text-[19px]" />
          ) : (
            <span className="font-display text-[19px] text-muted-foreground">—</span>
          )}
        </div>
        <p className="text-xs text-muted-foreground">{meta}</p>

        {isActionable && (
          <button
            type="button"
            onClick={(event) => {
              event.stopPropagation();
              onSelect();
            }}
            className={cn(
              "focus-sala mt-1.5 h-[52px] rounded-md text-base font-medium xl:hidden",
              isOccupied
                ? "bg-brand text-brand-foreground"
                : "bg-secondary text-secondary-foreground",
            )}
          >
            {TABLE_CTA[table.status]}
          </button>
        )}
      </div>
    </div>
  );
}

function TableGridSkeleton() {
  return (
    <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
      {Array.from({ length: 8 }).map((_, index) => (
        <div
          key={index}
          className="flex min-h-[190px] flex-col justify-between rounded-xl border bg-card p-[18px]"
        >
          <Skeleton className="h-11 w-16" />
          <div className="flex flex-col gap-2">
            <Skeleton className="h-3 w-20" />
            <Skeleton className="h-px w-full" />
            <Skeleton className="h-5 w-full" />
          </div>
        </div>
      ))}
    </div>
  );
}
