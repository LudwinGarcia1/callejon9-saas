"use client";

import { useState } from "react";
import { useQuery } from "@tanstack/react-query";

import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { Money } from "@/components/shared/money";
import { QueryState } from "@/components/shared/query-state";
import { StatusBadge } from "@/components/shared/status-badge";
import { api } from "@/lib/api";
import { endpoints } from "@/lib/endpoints";
import { queryKeys } from "@/lib/query-keys";
import { cn } from "@/lib/utils";
import type { OrderSummaryResponse, TableResponse } from "@/lib/types";
import { CheckoutPanel } from "./checkout-panel";

/** Una orden es cobrable cuando ya paso por el mesero: enviada a cocina o
 * lista. Una NEW todavia se esta armando en la mesa (el backend de hecho
 * permitiria cobrarla, pero no tiene sentido ofrecerla aqui) y una
 * PAID/CANCELED ya no admite cobro (409 si se intenta). */
const PAYABLE_STATUSES = new Set(["SENT", "READY"]);

/** Refresca la lista cada 10s, igual que el tablero de mesas del mesero: si
 * otro cajero cobra la misma orden, desaparece de esta lista sin recargar. */
const ORDERS_POLL_INTERVAL_MS = 10_000;

/**
 * Pantalla de caja: a la izquierda las ordenes cobrables, a la derecha el
 * detalle, propina y metodo de pago de la seleccionada.
 */
export function CashierView() {
  const [selectedOrderId, setSelectedOrderId] = useState<string | null>(null);

  const ordersQuery = useQuery({
    queryKey: queryKeys.orders.all(),
    queryFn: () => api.get<OrderSummaryResponse[]>(endpoints.orders.list()),
    refetchInterval: ORDERS_POLL_INTERVAL_MS,
  });
  const tablesQuery = useQuery({
    queryKey: queryKeys.tables.all(),
    queryFn: () => api.get<TableResponse[]>(endpoints.tables.list()),
  });

  const payableOrders = (ordersQuery.data ?? []).filter((order) =>
    PAYABLE_STATUSES.has(order.status),
  );

  function tableLabel(tableId: string | null): string {
    if (!tableId) {
      return "Para llevar";
    }
    const table = tablesQuery.data?.find((candidate) => candidate.id === tableId);
    return table ? `Mesa ${table.number}` : "Mesa";
  }

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-xl font-semibold">Caja</h1>
        <p className="text-sm text-muted-foreground">
          Selecciona una orden para revisar su cuenta y cobrarla.
        </p>
      </div>

      <div className="grid grid-cols-1 gap-6 lg:grid-cols-[minmax(0,1fr)_minmax(0,1.4fr)]">
        <Card>
          <CardHeader>
            <CardTitle>Ordenes por cobrar</CardTitle>
          </CardHeader>
          <CardContent>
            <QueryState
              isLoading={ordersQuery.isLoading}
              error={ordersQuery.error}
              isEmpty={payableOrders.length === 0}
              emptyMessage="No hay ordenes listas para cobrar."
            >
              {ordersQuery.isLoading ? (
                <div className="flex flex-col gap-2">
                  {Array.from({ length: 3 }).map((_, index) => (
                    <Skeleton key={index} className="h-16 w-full" />
                  ))}
                </div>
              ) : (
                <div className="flex flex-col gap-2">
                  {payableOrders.map((order) => (
                    <button
                      key={order.id}
                      type="button"
                      onClick={() => setSelectedOrderId(order.id)}
                      className={cn(
                        "flex flex-col gap-1 rounded-lg border p-3 text-left transition-colors hover:bg-muted",
                        selectedOrderId === order.id && "border-primary bg-muted",
                      )}
                    >
                      <div className="flex items-center justify-between gap-2">
                        <span className="font-medium">Orden {order.folio}</span>
                        <StatusBadge kind="order" status={order.status} />
                      </div>
                      <div className="flex items-center justify-between gap-2 text-sm text-muted-foreground">
                        <span>{tableLabel(order.tableId)}</span>
                        <Money amount={order.total} />
                      </div>
                    </button>
                  ))}
                </div>
              )}
            </QueryState>
          </CardContent>
        </Card>

        {selectedOrderId ? (
          <CheckoutPanel key={selectedOrderId} orderId={selectedOrderId} />
        ) : (
          <Card>
            <CardContent className="flex h-full items-center justify-center py-12">
              <p className="text-sm text-muted-foreground">
                Selecciona una orden de la lista para cobrarla.
              </p>
            </CardContent>
          </Card>
        )}
      </div>
    </div>
  );
}
