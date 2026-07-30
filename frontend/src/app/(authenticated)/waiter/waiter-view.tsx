"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";

import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { QueryState } from "@/components/shared/query-state";
import { StatusBadge } from "@/components/shared/status-badge";
import { api } from "@/lib/api";
import { endpoints } from "@/lib/endpoints";
import { queryKeys } from "@/lib/query-keys";
import { cn } from "@/lib/utils";
import type { OrderSummaryResponse, TableResponse, TableStatus } from "@/lib/types";
import { OpenTableDialog } from "./open-table-dialog";

/** Cada tantos milisegundos se refresca la lista de mesas, para que una mesa
 * liberada por caja aparezca sin que el mesero tenga que recargar. */
const TABLES_POLL_INTERVAL_MS = 10_000;

/** Estados de orden que cuentan como "todavia abierta" al buscar la orden de
 * una mesa ocupada. */
const OPEN_ORDER_STATUSES = new Set(["NEW", "SENT", "READY"]);

/** Acento de color por estado de mesa. El badge de estado (texto) sigue
 * viniendo de StatusBadge; esto solo colorea la tarjeta. */
const TABLE_STATUS_ACCENT: Record<TableStatus, string> = {
  FREE: "ring-1 ring-emerald-500/30 bg-emerald-500/5 hover:bg-emerald-500/10",
  OCCUPIED: "ring-1 ring-red-500/30 bg-red-500/5 hover:bg-red-500/10",
  RESERVED: "ring-1 ring-amber-500/30 bg-amber-500/5",
  CLEANING: "ring-1 ring-slate-400/30 bg-slate-400/5",
};

/**
 * Cuadricula de mesas para el mesero. Tocar una mesa libre abre el dialogo
 * para capturar comensales; tocar una ocupada busca su orden abierta en la
 * lista de ordenes (no existe un endpoint "orden de esta mesa") y navega ahi.
 */
export function WaiterView() {
  const router = useRouter();
  const queryClient = useQueryClient();
  const [selectedFreeTable, setSelectedFreeTable] = useState<TableResponse | null>(null);
  const [isResolvingTable, setIsResolvingTable] = useState<string | null>(null);

  const tablesQuery = useQuery({
    queryKey: queryKeys.tables.all(),
    queryFn: () => api.get<TableResponse[]>(endpoints.tables.list()),
    refetchInterval: TABLES_POLL_INTERVAL_MS,
  });

  async function handleOccupiedTable(table: TableResponse) {
    setIsResolvingTable(table.id);
    try {
      const orders = await queryClient.fetchQuery({
        queryKey: queryKeys.orders.all(),
        queryFn: () => api.get<OrderSummaryResponse[]>(endpoints.orders.list()),
      });
      const openOrder = orders.find(
        (order) => order.tableId === table.id && OPEN_ORDER_STATUSES.has(order.status),
      );

      if (!openOrder) {
        toast.error(`No se encontro una orden abierta para la mesa ${table.number}.`);
        return;
      }

      router.push(`/waiter/order/${openOrder.id}`);
    } catch {
      toast.error("No se pudo buscar la orden de esta mesa.");
    } finally {
      setIsResolvingTable(null);
    }
  }

  function handleTableClick(table: TableResponse) {
    if (table.status === "FREE") {
      setSelectedFreeTable(table);
      return;
    }

    if (table.status === "OCCUPIED") {
      void handleOccupiedTable(table);
    }
  }

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-xl font-semibold">Mesas</h1>
        <p className="text-sm text-muted-foreground">
          Toca una mesa libre para abrir una orden, o una ocupada para continuarla.
        </p>
      </div>

      <QueryState
        isLoading={tablesQuery.isLoading}
        error={tablesQuery.error}
        isEmpty={tablesQuery.data?.length === 0}
        emptyMessage="Todavia no hay mesas registradas."
      >
        {tablesQuery.isLoading ? (
          <TableGridSkeleton />
        ) : (
          <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-4">
            {tablesQuery.data?.map((table) => {
              const isClickable = table.status === "FREE" || table.status === "OCCUPIED";
              const isResolving = isResolvingTable === table.id;

              return (
                <Card
                  key={table.id}
                  onClick={() => isClickable && !isResolving && handleTableClick(table)}
                  className={cn(
                    "transition-colors",
                    TABLE_STATUS_ACCENT[table.status],
                    isClickable ? "cursor-pointer" : "cursor-not-allowed opacity-70",
                  )}
                >
                  <CardHeader>
                    <CardTitle className="text-2xl">Mesa {table.number}</CardTitle>
                  </CardHeader>
                  <CardContent className="flex flex-col gap-2">
                    <p className="text-sm text-muted-foreground">
                      {table.capacity} comensales
                    </p>
                    <StatusBadge kind="table" status={table.status} />
                    {isResolving && (
                      <p className="text-xs text-muted-foreground">Buscando orden...</p>
                    )}
                  </CardContent>
                </Card>
              );
            })}
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
    </div>
  );
}

function TableGridSkeleton() {
  return (
    <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-4">
      {Array.from({ length: 6 }).map((_, index) => (
        <Skeleton key={index} className="h-32 w-full" />
      ))}
    </div>
  );
}
