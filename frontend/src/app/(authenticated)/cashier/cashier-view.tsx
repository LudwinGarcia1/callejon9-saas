"use client";

import { useState } from "react";
import { useQuery } from "@tanstack/react-query";

import { Skeleton } from "@/components/ui/skeleton";
import { TenantBadge } from "@/components/layout/tenant-badge";
import { Money } from "@/components/shared/money";
import { QueryState } from "@/components/shared/query-state";
import { statusLabel, statusTone } from "@/components/shared/status-badge";
import { useSession } from "@/hooks/use-session";
import { api } from "@/lib/api";
import { endpoints } from "@/lib/endpoints";
import { formatShortTime } from "@/lib/format";
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
 * Pantalla de caja en tres columnas: la cola de cobro, el ticket de la orden
 * seleccionada y el resumen con el boton de cobro.
 *
 * Va siempre en modo oscuro, aunque el restaurante haya elegido claro: es una
 * estacion fija de muchas horas y el total tiene que leerse a distancia.
 */
export function CashierView() {
  const { user } = useSession();
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
  const payableTotal = payableOrders.reduce((sum, order) => sum + order.total, 0);

  function tableLabel(tableId: string | null): string {
    if (!tableId) {
      return "Para llevar";
    }
    const table = tablesQuery.data?.find((candidate) => candidate.id === tableId);
    return table ? `Mesa ${table.number}` : "Mesa";
  }

  return (
    // El modo oscuro de esta pantalla lo aplica el layout autenticado, que lo
    // pone tambien en <html> para que dialogos y avisos vayan a juego.
    <div className="flex flex-1 flex-col bg-background text-foreground xl:grid xl:grid-cols-[330px_minmax(0,1fr)_400px]">
      <section className="flex flex-col bg-surface-alt xl:border-r">
        <div className="border-b px-[22px] pt-[22px] pb-[18px]">
          <TenantBadge
            restaurantName={user?.restaurantName}
            size="sm"
            className="mb-[18px]"
          />
          <p className="eyebrow">
            Por cobrar · {payableOrders.length}{" "}
            {payableOrders.length === 1 ? "cuenta" : "cuentas"}
          </p>
          <Money amount={payableTotal} className="mt-1 block font-display text-[38px] leading-none" />
        </div>

        <div className="flex flex-col">
          <QueryState
            isLoading={ordersQuery.isLoading}
            error={ordersQuery.error}
            isEmpty={payableOrders.length === 0}
            emptyMessage="Ninguna orden por cobrar."
            skeleton={
              <div className="flex flex-col gap-px p-[19px]">
                {Array.from({ length: 4 }).map((_, index) => (
                  <Skeleton key={index} className="h-[72px] w-full" />
                ))}
              </div>
            }
          >
            {payableOrders.map((order) => {
              const isSelected = selectedOrderId === order.id;
              return (
                <button
                  key={order.id}
                  type="button"
                  data-tone={statusTone({ kind: "order", status: order.status })}
                  onClick={() => setSelectedOrderId(order.id)}
                  aria-pressed={isSelected}
                  className={cn(
                    "focus-sala flex flex-col gap-2 border-b border-l-[3px] px-[19px] py-4 text-left",
                    isSelected
                      ? "border-l-brand bg-brand-tint"
                      : "border-l-transparent hover:bg-accent/60",
                  )}
                >
                  <div className="flex items-baseline justify-between gap-3">
                    <span className="text-base font-medium">{tableLabel(order.tableId)}</span>
                    <Money amount={order.total} className="font-display text-[24px]" />
                  </div>
                  <div className="flex items-center justify-between gap-3 font-mono text-[10px] tracking-[0.1em] text-muted-foreground uppercase">
                    <span>
                      {order.folio} · {formatShortTime(order.openedAt)}
                    </span>
                    <span className="text-[var(--tone)]">
                      {statusLabel({ kind: "order", status: order.status })}
                    </span>
                  </div>
                </button>
              );
            })}
          </QueryState>
        </div>
      </section>

      {selectedOrderId ? (
        <CheckoutPanel key={selectedOrderId} orderId={selectedOrderId} />
      ) : (
        <div className="flex items-center justify-center px-7 py-16 xl:col-span-2">
          <div className="text-center">
            <p className="eyebrow">Sin selección</p>
            <p className="mt-1 text-sm text-muted-foreground">
              Selecciona una orden de la lista para cobrarla.
            </p>
          </div>
        </div>
      )}
    </div>
  );
}
