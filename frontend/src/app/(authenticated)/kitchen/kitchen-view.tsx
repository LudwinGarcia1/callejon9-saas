"use client";

import { useEffect, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";

import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Separator } from "@/components/ui/separator";
import { Skeleton } from "@/components/ui/skeleton";
import { QueryState } from "@/components/shared/query-state";
import { StatusBadge } from "@/components/shared/status-badge";
import { ApiError, api } from "@/lib/api";
import { endpoints } from "@/lib/endpoints";
import { elapsedLabel, orderAge, type OrderAge } from "@/lib/kitchen-timing";
import { queryKeys } from "@/lib/query-keys";
import { cn } from "@/lib/utils";
import {
  KITCHEN_STATUS_LABELS,
  type KitchenItemResponse,
  type KitchenItemStatus,
  type KitchenOrderResponse,
  type TableResponse,
  type UpdateKitchenItemStatusRequest,
} from "@/lib/types";

const KITCHEN_POLL_INTERVAL_MS = 5_000;
const CLOCK_TICK_MS = 30_000;

const AGE_CARD_STYLES: Record<OrderAge, string> = {
  normal: "",
  warning: "border-2 border-[var(--state-warning)]",
  critical: "border-2 border-[var(--state-critical)] bg-[var(--state-critical)]/10",
};

const AGE_TEXT_STYLES: Record<OrderAge, string> = {
  normal: "text-muted-foreground",
  warning: "text-[var(--state-warning)] font-medium",
  critical: "text-[var(--state-critical)] font-semibold",
};

const FORWARD_SEQUENCE: KitchenItemStatus[] = [
  "PENDING",
  "IN_PREPARATION",
  "READY",
  "DELIVERED",
];

function nextKitchenStatus(current: KitchenItemStatus): KitchenItemStatus | null {
  const index = FORWARD_SEQUENCE.indexOf(current);
  return index === -1 || index === FORWARD_SEQUENCE.length - 1
    ? null
    : FORWARD_SEQUENCE[index + 1];
}

function isReadyOrBeyond(status: KitchenItemStatus): boolean {
  return status === "READY" || status === "DELIVERED";
}

export function KitchenView() {
  const queryClient = useQueryClient();
  const [now, setNow] = useState(() => Date.now());

  useEffect(() => {
    const id = setInterval(() => setNow(Date.now()), CLOCK_TICK_MS);
    return () => clearInterval(id);
  }, []);

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

      if (
        order?.items.every((item) =>
          item.id === updatedItem.id
            ? isReadyOrBeyond(updatedItem.kitchenStatus)
            : isReadyOrBeyond(item.kitchenStatus),
        ) &&
        updatedItem.kitchenStatus === "READY"
      ) {
        toast.info(
          `Todos los productos de la orden ${order.folio} están listos. Pasará a "Lista" en el tablero.`,
        );
      }
    },
    onError: (error) => {
      toast.error(error instanceof ApiError ? error.message : "No se pudo actualizar el producto.");
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
    <div className="flex flex-col gap-6" data-density="spacious">
      <div>
        <h1 className="text-xl font-semibold">Cocina</h1>
        <p className="text-sm text-muted-foreground">
          Órdenes enviadas a cocina, de la más antigua a la más reciente.
        </p>
      </div>

      <QueryState
        isLoading={ordersQuery.isLoading}
        error={ordersQuery.error}
        isEmpty={ordersQuery.data?.length === 0}
        emptyMessage="No hay órdenes en cocina en este momento."
      >
        {ordersQuery.isLoading ? (
          <BoardSkeleton />
        ) : (
          <div className="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-3">
            {ordersQuery.data?.map((order) => {
              const age = orderAge(order.sentToKitchenAt, now);
              return (
                <Card key={order.id} className={AGE_CARD_STYLES[age]}>
                  <CardHeader className="flex flex-row items-start justify-between gap-2">
                    <div>
                      <CardTitle className="text-[length:var(--density-text-lg)]">
                        {tableLabel(order.tableId)}
                      </CardTitle>
                      <p className={cn("text-[length:var(--density-text-base)]", AGE_TEXT_STYLES[age])}>
                        {elapsedLabel(order.sentToKitchenAt, now)}
                      </p>
                      <p className="text-[length:var(--density-text-sm)] text-muted-foreground">
                        Orden {order.folio}
                      </p>
                    </div>
                    <StatusBadge kind="order" status={order.status} />
                  </CardHeader>
                  <CardContent className="flex flex-col gap-3">
                    {order.items.map((item, index) => {
                      const next = nextKitchenStatus(item.kitchenStatus);
                      const isPending =
                        advanceItemMutation.isPending &&
                        advanceItemMutation.variables?.itemId === item.id;
                      return (
                        <div key={item.id} className="flex flex-col gap-2">
                          {index > 0 && <Separator />}
                          <div
                            className={cn(
                              "flex items-center justify-between gap-2",
                              isReadyOrBeyond(item.kitchenStatus) && "opacity-50",
                            )}
                          >
                            <div>
                              <p className="text-[length:var(--density-text-base)] font-medium">
                                {item.quantity} × {item.productName}
                              </p>
                              {item.notes && (
                                <p className="text-[length:var(--density-text-sm)] text-muted-foreground">
                                  {item.notes}
                                </p>
                              )}
                            </div>
                            <StatusBadge kind="kitchen" status={item.kitchenStatus} />
                          </div>
                          {next && (
                            <Button
                              variant="outline"
                              disabled={isPending}
                              className="h-[var(--control-height)] w-full text-[length:var(--density-text-base)]"
                              onClick={() =>
                                advanceItemMutation.mutate({ itemId: item.id, status: next })
                              }
                            >
                              {isPending ? "Actualizando..." : `Marcar como ${KITCHEN_STATUS_LABELS[next]}`}
                            </Button>
                          )}
                        </div>
                      );
                    })}
                  </CardContent>
                </Card>
              );
            })}
          </div>
        )}
      </QueryState>
    </div>
  );
}

function BoardSkeleton() {
  return (
    <div className="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-3">
      {Array.from({ length: 3 }).map((_, index) => (
        <Skeleton key={index} className="h-48 w-full" />
      ))}
    </div>
  );
}
