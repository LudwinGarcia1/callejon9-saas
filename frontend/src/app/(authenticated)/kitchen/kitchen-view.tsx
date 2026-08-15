"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";

import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { ScreenMetric, ScreenShell } from "@/components/layout/screen-shell";
import { QueryState } from "@/components/shared/query-state";
import { StatusBadge } from "@/components/shared/status-badge";
import { ApiError, api } from "@/lib/api";
import { endpoints } from "@/lib/endpoints";
import { formatShortTime } from "@/lib/format";
import { queryKeys } from "@/lib/query-keys";
import {
  KITCHEN_STATUS_LABELS,
  type KitchenItemResponse,
  type KitchenItemStatus,
  type KitchenOrderResponse,
  type TableResponse,
  type UpdateKitchenItemStatusRequest,
} from "@/lib/types";

/** Cinco segundos es imperceptible de "en vivo" para quien mira el tablero,
 * y evita la complejidad de un STOMP cross-origin que el proxy de Next no
 * reenvia. Se apaga en segundo plano para no seguir golpeando al backend
 * con una pestaña sin foco. */
const KITCHEN_POLL_INTERVAL_MS = 5_000;

/** Espejo exacto de KitchenService.FORWARD_SEQUENCE: solo sirve para decidir
 * que boton ofrecer, nunca como fuente de verdad del estado real. */
const FORWARD_SEQUENCE: KitchenItemStatus[] = [
  "PENDING",
  "IN_PREPARATION",
  "READY",
  "DELIVERED",
];

function nextKitchenStatus(current: KitchenItemStatus): KitchenItemStatus | null {
  const index = FORWARD_SEQUENCE.indexOf(current);
  if (index === -1 || index === FORWARD_SEQUENCE.length - 1) {
    return null;
  }
  return FORWARD_SEQUENCE[index + 1];
}

/** Un item cuenta como "listo o mas alla" para efectos de aviso local de que
 * la orden esta a punto de salir del tablero (el backend es quien decide de
 * verdad, aqui solo se anuncia). */
function isReadyOrBeyond(status: KitchenItemStatus): boolean {
  return status === "READY" || status === "DELIVERED";
}

/**
 * Tablero de cocina: ordenes enviadas (SENT), mas antigua primero tal como
 * las entrega KitchenService.listSentOrders. Cada tarjeta lista sus
 * productos con su estado de cocina y un boton para avanzar un solo paso.
 *
 * Cuando el backend detecta que todos los productos de una orden llegaron a
 * READY, la promueve a READY por su cuenta y KitchenController.listSentOrders
 * deja de devolverla (solo lista SENT): por eso la tarjeta desaparece del
 * tablero en el siguiente refetch, que es la forma honesta de "surfacing"
 * pedida — nunca se calcula el estado de la orden en el cliente.
 */
export function KitchenView() {
  const queryClient = useQueryClient();

  const ordersQuery = useQuery({
    queryKey: queryKeys.kitchen.orders(),
    queryFn: () => api.get<KitchenOrderResponse[]>(endpoints.kitchen.orders()),
    refetchInterval: KITCHEN_POLL_INTERVAL_MS,
    refetchIntervalInBackground: false,
  });

  const tablesQuery = useQuery({
    queryKey: queryKeys.tables.all(),
    queryFn: () => api.get<TableResponse[]>(endpoints.tables.list()),
  });

  const advanceItemMutation = useMutation({
    mutationFn: ({ itemId, status }: { itemId: string; status: KitchenItemStatus }) =>
      api.post<KitchenItemResponse>(endpoints.kitchen.itemStatus(itemId), {
        status,
      } satisfies UpdateKitchenItemStatusRequest),
    onSuccess: (updatedItem) => {
      const orders = queryClient.getQueryData<KitchenOrderResponse[]>(
        queryKeys.kitchen.orders(),
      );
      const order = orders?.find((candidate) => candidate.id === updatedItem.orderId);

      queryClient.invalidateQueries({ queryKey: queryKeys.kitchen.orders() });
      toast.success(
        `${updatedItem.productName} -> ${KITCHEN_STATUS_LABELS[updatedItem.kitchenStatus]}.`,
      );

      if (order) {
        const everyItemReadyOrBeyond = order.items.every((item) =>
          item.id === updatedItem.id
            ? isReadyOrBeyond(updatedItem.kitchenStatus)
            : isReadyOrBeyond(item.kitchenStatus),
        );
        if (everyItemReadyOrBeyond && updatedItem.kitchenStatus === "READY") {
          toast.info(
            `Todos los productos de la orden ${order.folio} están listos. Pasará a "Lista" en el tablero.`,
          );
        }
      }
    },
    onError: (error) => {
      toast.error(
        error instanceof ApiError ? error.message : "No se pudo actualizar el producto.",
      );
    },
  });

  function tableLabel(tableId: string | null): string {
    if (!tableId) {
      return "Para llevar";
    }
    const table = tablesQuery.data?.find((candidate) => candidate.id === tableId);
    return table ? `Mesa ${table.number}` : "Mesa";
  }

  return (
    // Cocina es estacion fija de turno largo: va en oscuro aunque el
    // restaurante haya elegido modo claro. Lo aplica el layout autenticado.
    <div className="flex flex-1 flex-col bg-background text-foreground">
      <ScreenShell
        title="Cocina"
        subtitle="Órdenes enviadas a cocina, de la más antigua a la más reciente."
        actions={
          <ScreenMetric label="En preparación" value={ordersQuery.data?.length ?? "—"} />
        }
      >
        <QueryState
          isLoading={ordersQuery.isLoading}
          error={ordersQuery.error}
          isEmpty={ordersQuery.data?.length === 0}
          emptyMessage="No hay órdenes en cocina en este momento."
          skeleton={<BoardSkeleton />}
        >
          <div className="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-3">
            {ordersQuery.data?.map((order) => (
              <article
                key={order.id}
                className="flex flex-col rounded-xl border bg-card p-[18px]"
              >
                <header className="flex items-start justify-between gap-3 border-b pb-3.5">
                  <div>
                    <p className="eyebrow">
                      {order.folio}
                      {order.sentToKitchenAt
                        ? ` · enviada ${formatShortTime(order.sentToKitchenAt)}`
                        : ""}
                    </p>
                    <p className="mt-0.5 font-display text-[28px] leading-none">
                      {tableLabel(order.tableId)}
                    </p>
                  </div>
                  <StatusBadge kind="order" status={order.status} />
                </header>

                <div className="flex flex-col">
                  {order.items.map((item) => {
                    const next = nextKitchenStatus(item.kitchenStatus);
                    const isPending =
                      advanceItemMutation.isPending &&
                      advanceItemMutation.variables?.itemId === item.id;

                    return (
                      <div
                        key={item.id}
                        className="flex flex-col gap-2.5 border-b border-dotted border-border-strong py-3.5 last:border-b-0"
                      >
                        <div className="flex items-start justify-between gap-3">
                          <div className="min-w-0">
                            <p className="text-[15px]">
                              <span className="font-mono text-[13px] text-muted-foreground">
                                {item.quantity}×
                              </span>{" "}
                              {item.productName}
                            </p>
                            {item.notes && (
                              <p className="text-xs text-muted-foreground">{item.notes}</p>
                            )}
                          </div>
                          <StatusBadge kind="kitchen" status={item.kitchenStatus} />
                        </div>
                        {next && (
                          <Button
                            variant="outline"
                            className="h-11 w-full justify-center"
                            disabled={isPending}
                            onClick={() =>
                              advanceItemMutation.mutate({ itemId: item.id, status: next })
                            }
                          >
                            {isPending
                              ? "Actualizando…"
                              : `Marcar como ${KITCHEN_STATUS_LABELS[next]}`}
                          </Button>
                        )}
                      </div>
                    );
                  })}
                </div>
              </article>
            ))}
          </div>
        </QueryState>
      </ScreenShell>
    </div>
  );
}

function BoardSkeleton() {
  return (
    <div className="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-3">
      {Array.from({ length: 3 }).map((_, index) => (
        <Skeleton key={index} className="h-56 w-full rounded-xl" />
      ))}
    </div>
  );
}
