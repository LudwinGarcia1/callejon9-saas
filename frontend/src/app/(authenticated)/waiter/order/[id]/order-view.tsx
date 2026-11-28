"use client";

import { useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { CheckCircle2Icon, CheckIcon, XIcon } from "lucide-react";
import { toast } from "sonner";

import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Skeleton } from "@/components/ui/skeleton";
import { Money } from "@/components/shared/money";
import { QueryState } from "@/components/shared/query-state";
import { StatusBadge } from "@/components/shared/status-badge";
import { useKitchenChannel } from "@/hooks/use-kitchen-channel";
import { useSession } from "@/hooks/use-session";
import { ApiError, api } from "@/lib/api";
import { endpoints } from "@/lib/endpoints";
import { formatShortTime } from "@/lib/format";
import { queryKeys } from "@/lib/query-keys";
import { kitchenEventOrderId } from "@/lib/realtime";
import type {
  AddOrderItemsRequest,
  CategoryResponse,
  KitchenItemResponse,
  KitchenItemStatus,
  OrderResponse,
  OrderStatus,
  ProductResponse,
  TableResponse,
  UpdateKitchenItemStatusRequest,
} from "@/lib/types";
import { ProductPicker, type CartLine } from "./product-picker";

interface OrderViewProps {
  orderId: string;
}

/** Estados en los que la orden todavia se puede cancelar: coinciden con la
 * guarda de {@code OrderService.cancelOrder}. El mesero solo cancela antes de
 * enviar a cocina; despues, la cancelacion queda reservada al administrador. */
const WAITER_CANCELABLE_ORDER_STATUSES = new Set<OrderStatus>(["NEW"]);
const ADMIN_CANCELABLE_ORDER_STATUSES = new Set<OrderStatus>(["NEW", "SENT", "READY"]);

/** Mismo tope que {@code AddOrderItemRequest.quantity} en el backend. */
const MAX_LINE_QUANTITY = 99;

/** Estados en los que cocina puede cambiar la orden sin que el mesero haga nada. */
const IN_KITCHEN_ORDER_STATUSES = new Set<OrderStatus>(["SENT", "READY"]);

/** Respaldo del canal en tiempo real mientras la orden esta en cocina (CAL-170). */
const IN_KITCHEN_POLL_INTERVAL_MS = 10_000;
const IN_KITCHEN_POLL_INTERVAL_LIVE_MS = 30_000;

/**
 * Pantalla de una orden. La orden manda y ocupa la columna ancha; el catalogo
 * es un panel de apoyo de 430px a la derecha. El carrito nunca se envia
 * producto por producto: se junta localmente y se confirma en un solo POST por
 * lote.
 */
export function OrderView({ orderId }: OrderViewProps) {
  const router = useRouter();
  const { user } = useSession();
  const queryClient = useQueryClient();
  const [cart, setCart] = useState<CartLine[]>([]);
  const nextLineKey = useRef(0);
  const [confirmCancelOpen, setConfirmCancelOpen] = useState(false);

  // Cocina avanza los productos desde otra pantalla: el evento de esta orden
  // la vuelve a pedir, y con ella su estado («Lista» cuando todo esta listo).
  const { connected } = useKitchenChannel((event) => {
    if (kitchenEventOrderId(event) === orderId) {
      queryClient.invalidateQueries({ queryKey: queryKeys.orders.detail(orderId) });
      queryClient.invalidateQueries({ queryKey: queryKeys.orders.open() });
    }
  });

  const orderQuery = useQuery({
    queryKey: queryKeys.orders.detail(orderId),
    queryFn: () => api.get<OrderResponse>(endpoints.orders.detail(orderId)),
    refetchInterval: (query) => {
      const status = query.state.data?.status;
      if (!status || !IN_KITCHEN_ORDER_STATUSES.has(status)) {
        return false;
      }
      return connected ? IN_KITCHEN_POLL_INTERVAL_LIVE_MS : IN_KITCHEN_POLL_INTERVAL_MS;
    },
    refetchIntervalInBackground: false,
  });
  const tablesQuery = useQuery({
    queryKey: queryKeys.tables.all(),
    queryFn: () => api.get<TableResponse[]>(endpoints.tables.list()),
  });
  const categoriesQuery = useQuery({
    queryKey: queryKeys.categories.all(),
    queryFn: () => api.get<CategoryResponse[]>(endpoints.categories.list()),
  });
  const productsQuery = useQuery({
    queryKey: queryKeys.products.all(),
    queryFn: () => api.get<ProductResponse[]>(endpoints.products.list()),
  });

  const addItemsMutation = useMutation({
    mutationFn: (lines: CartLine[]) =>
      api.post<OrderResponse>(endpoints.orders.items(orderId), {
        items: lines.map((line) => {
          const notes = line.notes.trim();
          return {
            productId: line.product.id,
            quantity: line.quantity,
            ...(notes ? { notes } : {}),
          };
        }),
      } satisfies AddOrderItemsRequest),
    onSuccess: (order) => {
      // El endpoint ya devuelve la orden completa (200): se guarda tal cual,
      // sin refetch ni riesgo de deshacer un update optimista.
      queryClient.setQueryData(queryKeys.orders.detail(orderId), order);
      queryClient.invalidateQueries({ queryKey: queryKeys.orders.all() });
      toast.success("Productos agregados a la orden.");
      setCart([]);
    },
    onError: (error) => {
      toast.error(
        error instanceof ApiError ? error.message : "No se pudieron agregar los productos.",
      );
    },
  });

  /** Quita una linea ya agregada mientras la orden sigue en NEW: corrige un
   * error de captura sin cancelar toda la orden. Como agregar, el endpoint
   * devuelve la orden completa con el total recalculado. */
  const removeItemMutation = useMutation({
    mutationFn: (itemId: string) =>
      api.del<OrderResponse>(endpoints.orders.item(orderId, itemId)),
    onSuccess: (order) => {
      queryClient.setQueryData(queryKeys.orders.detail(orderId), order);
      queryClient.invalidateQueries({ queryKey: queryKeys.orders.all() });
      toast.success("Producto quitado de la orden.");
    },
    onError: (error) => {
      toast.error(error instanceof ApiError ? error.message : "No se pudo quitar el producto.");
    },
  });

  const sendToKitchenMutation = useMutation({
    mutationFn: () => api.post<OrderResponse>(endpoints.orders.sendToKitchen(orderId)),
    onSuccess: (order) => {
      queryClient.setQueryData(queryKeys.orders.detail(orderId), order);
      queryClient.invalidateQueries({ queryKey: queryKeys.orders.all() });
      toast.success("Orden enviada a cocina.");
    },
    onError: (error) => {
      toast.error(
        error instanceof ApiError ? error.message : "No se pudo enviar la orden a cocina.",
      );
    },
  });

  /**
   * Cancela la orden y libera la mesa (lo hace el backend en la misma
   * transaccion). El estado puede haber cambiado desde que se cargo la
   * pantalla — otro mesero la cobro, por ejemplo — asi que el 409 se
   * muestra como aviso normal en vez de dejar que se vea como una falla.
   */
  const cancelOrderMutation = useMutation({
    mutationFn: () => api.post<OrderResponse>(endpoints.orders.cancel(orderId)),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.orders.all() });
      queryClient.invalidateQueries({ queryKey: queryKeys.tables.all() });
      setConfirmCancelOpen(false);
      toast.success("Orden cancelada. La mesa quedó libre.");
      router.push("/waiter");
    },
    onError: (error) => {
      setConfirmCancelOpen(false);
      toast.error(error instanceof ApiError ? error.message : "No se pudo cancelar la orden.");
    },
  });

  const advanceItemMutation = useMutation({
    mutationFn: ({ itemId, status }: { itemId: string; status: KitchenItemStatus }) =>
      api.post<KitchenItemResponse>(endpoints.kitchen.itemStatus(itemId), {
        status,
      } satisfies UpdateKitchenItemStatusRequest),
    onSuccess: (updated) => {
      queryClient.invalidateQueries({ queryKey: queryKeys.orders.detail(orderId) });
      queryClient.invalidateQueries({ queryKey: queryKeys.orders.all() });
      toast.success(
        updated.kitchenStatus === "DELIVERED"
          ? `${updated.productName} marcado como entregado a la mesa.`
          : `${updated.productName} actualizado.`,
      );
    },
    onError: (error) => {
      toast.error(
        error instanceof ApiError ? error.message : "No se pudo actualizar el estado del platillo.",
      );
    },
  });

  const deliverAllReadyItemsMutation = useMutation({
    mutationFn: async (itemIds: string[]) => {
      for (const id of itemIds) {
        await api.post(endpoints.kitchen.itemStatus(id), { status: "DELIVERED" });
      }
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.orders.detail(orderId) });
      queryClient.invalidateQueries({ queryKey: queryKeys.orders.all() });
      toast.success("Todos los platillos listos se marcaron como entregados.");
    },
    onError: (error) => {
      toast.error(
        error instanceof ApiError ? error.message : "No se pudieron marcar los platillos como entregados.",
      );
    },
  });

  /** Volver a tocar un producto suma una unidad a su linea sin nota. Si todas
   * sus lineas ya llevan nota, abre una nueva: la unidad extra no hereda una
   * indicacion que el comensal no pidio. */
  function addProductToCart(product: ProductResponse) {
    setCart((previous) => {
      const plain = previous.find(
        (line) => line.product.id === product.id && line.notes.trim() === "",
      );
      if (plain) {
        return previous.map((line) =>
          line.key === plain.key
            ? { ...line, quantity: Math.min(line.quantity + 1, MAX_LINE_QUANTITY) }
            : line,
        );
      }
      nextLineKey.current += 1;
      return [
        ...previous,
        { key: `${product.id}-${nextLineKey.current}`, product, quantity: 1, notes: "" },
      ];
    });
  }

  function incrementLine(lineKey: string) {
    setCart((previous) =>
      previous.map((line) =>
        line.key === lineKey
          ? { ...line, quantity: Math.min(line.quantity + 1, MAX_LINE_QUANTITY) }
          : line,
      ),
    );
  }

  function decrementLine(lineKey: string) {
    setCart((previous) =>
      previous.flatMap((line) => {
        if (line.key !== lineKey) {
          return [line];
        }
        if (line.quantity <= 1) {
          return [];
        }
        return [{ ...line, quantity: line.quantity - 1 }];
      }),
    );
  }

  function removeLine(lineKey: string) {
    setCart((previous) => previous.filter((line) => line.key !== lineKey));
  }

  function changeLineNotes(lineKey: string, notes: string) {
    setCart((previous) =>
      previous.map((line) => (line.key === lineKey ? { ...line, notes } : line)),
    );
  }

  const order = orderQuery.data;
  const table = tablesQuery.data?.find((candidate) => candidate.id === order?.tableId);
  const cancelableStatuses =
    user?.role === "ADMIN" ? ADMIN_CANCELABLE_ORDER_STATUSES : WAITER_CANCELABLE_ORDER_STATUSES;
  const isCancelable = order ? cancelableStatuses.has(order.status) : false;

  return (
    <QueryState
      isLoading={orderQuery.isLoading}
      error={orderQuery.error}
      isEmpty={!order}
      emptyMessage="No se encontró la orden."
      skeleton={<OrderSkeleton />}
    >
      {order && (
        <div className="flex flex-1 flex-col xl:grid xl:grid-cols-[minmax(0,1fr)_430px]">
          <section className="flex flex-col xl:border-r">
            <header className="flex flex-wrap items-end justify-between gap-4 border-b px-[18px] pt-4 pb-4 sm:px-7 sm:pt-[26px] sm:pb-[18px]">
              <div>
                <p className="eyebrow">
                  {order.folio} · abierta {formatShortTime(order.openedAt)}
                </p>
                <h1 className="mt-0.5 font-display text-[28px] leading-none font-normal sm:text-[36px]">
                  {table ? `Mesa ${table.number}` : "Para llevar"} · {order.guestCount}{" "}
                  {order.guestCount === 1 ? "comensal" : "comensales"}
                </h1>
              </div>
              <StatusBadge kind="order" status={order.status} />
            </header>

            <div className="flex flex-1 flex-col px-[18px] py-5 sm:px-7">
              {order.items.length === 0 ? (
                <div className="py-4">
                  <p className="eyebrow">Comanda vacía</p>
                  <p className="mt-1 text-sm text-muted-foreground">
                    Aún no se han agregado productos a esta orden.
                  </p>
                </div>
              ) : (
                <>
                  <div className="eyebrow flex items-center gap-3 border-b pb-2">
                    <span className="w-[34px]">Cant</span>
                    <span className="flex-1">Producto</span>
                    <span className="hidden sm:inline">Precio</span>
                    <span className="w-24 text-right">Subtotal</span>
                  </div>
                  {order.items.map((item) => (
                    <div
                      key={item.id}
                      className="flex items-center gap-3 border-b border-dotted border-border-strong py-[13px]"
                    >
                      <span className="w-[34px] font-mono text-[13px] text-muted-foreground">
                        {item.quantity}×
                      </span>
                      <div className="min-w-0 flex-1">
                        <p className="text-[15px]">{item.productName}</p>
                        {item.notes && (
                          <p className="text-xs text-muted-foreground">{item.notes}</p>
                        )}
                      </div>
                      {/* Antes de enviar a cocina todo esta "Pendiente": solo
                          aporta una vez que cocina puede moverlo. */}
                      {order.status !== "NEW" && (
                        <div className="flex items-center gap-2">
                          <StatusBadge kind="kitchen" status={item.kitchenStatus} />
                          {item.kitchenStatus === "READY" && (
                            <Button
                              type="button"
                              variant="outline"
                              size="sm"
                              className="h-6 px-2 text-xs border-emerald-500/40 text-emerald-700 hover:bg-emerald-50 hover:text-emerald-800 dark:text-emerald-400 dark:hover:bg-emerald-950/40"
                              disabled={advanceItemMutation.isPending}
                              onClick={() =>
                                advanceItemMutation.mutate({
                                  itemId: item.id,
                                  status: "DELIVERED",
                                })
                              }
                            >
                              <CheckIcon className="mr-1 size-3" />
                              Entregar
                            </Button>
                          )}
                        </div>
                      )}
                      <Money
                        amount={item.unitPrice}
                        className="hidden font-mono text-[13px] text-muted-foreground sm:inline"
                      />
                      <Money
                        amount={item.unitPrice * item.quantity}
                        className="w-24 text-right text-[15px]"
                      />
                      {order.status === "NEW" && (
                        <Button
                          type="button"
                          variant="ghost"
                          size="icon-sm"
                          disabled={removeItemMutation.isPending}
                          onClick={() => removeItemMutation.mutate(item.id)}
                          aria-label={`Quitar ${item.productName} de la orden`}
                        >
                          <XIcon />
                        </Button>
                      )}
                    </div>
                  ))}
                  {order.status === "NEW" && (
                    <p className="mt-2.5 text-xs text-muted-foreground">
                      Antes de enviar a cocina puedes quitar un producto con la{" "}
                      <span aria-hidden>×</span> de su línea.
                    </p>
                  )}
                </>
              )}

              <div className="mt-auto pt-6">
                <div className="flex items-baseline justify-between gap-4 border-t border-border-strong pt-4">
                  <span className="eyebrow">Total de la orden</span>
                  <Money
                    amount={order.total}
                    className="font-display text-[34px] leading-none sm:text-[44px]"
                  />
                </div>

                <div className="mt-[18px] flex flex-col gap-2.5 sm:flex-row">
                  <SendToKitchenAction
                    order={order}
                    cartIsEmpty={cart.length === 0}
                    isPending={sendToKitchenMutation.isPending}
                    onSend={() => sendToKitchenMutation.mutate()}
                    onDeliverAllReady={() => {
                      const readyItemIds = order.items
                        .filter((it) => it.kitchenStatus === "READY")
                        .map((it) => it.id);
                      if (readyItemIds.length > 0) {
                        deliverAllReadyItemsMutation.mutate(readyItemIds);
                      }
                    }}
                    isDeliveringAll={deliverAllReadyItemsMutation.isPending}
                  />
                  {isCancelable && (
                    <Button
                      variant="outline"
                      size="xl"
                      className="text-brand sm:w-[180px]"
                      onClick={() => setConfirmCancelOpen(true)}
                    >
                      Cancelar orden
                    </Button>
                  )}
                </div>

                {isCancelable && (
                  <p className="mt-2.5 text-xs text-muted-foreground">
                    Cancelar libera la mesa de inmediato. Los productos quedan en el historial.
                  </p>
                )}
              </div>
            </div>
          </section>

          <ProductPicker
            categories={categoriesQuery.data ?? []}
            products={productsQuery.data ?? []}
            isLoading={categoriesQuery.isLoading || productsQuery.isLoading}
            cart={cart}
            onAddProduct={addProductToCart}
            onIncrement={incrementLine}
            onDecrement={decrementLine}
            onRemove={removeLine}
            onNotesChange={changeLineNotes}
            onCommit={() => addItemsMutation.mutate(cart)}
            isCommitting={addItemsMutation.isPending}
            disabled={order.status === "PAID" || order.status === "CANCELED"}
            alreadySentToKitchen={order.status === "SENT" || order.status === "READY"}
            orderStatus={order.status}
          />

          <Dialog open={confirmCancelOpen} onOpenChange={setConfirmCancelOpen}>
            <DialogContent>
              <DialogHeader>
                <DialogTitle>Cancelar orden {order.folio}</DialogTitle>
                <DialogDescription>
                  Esta acción no se puede deshacer. Los productos ya agregados se conservan en
                  el historial, pero la orden queda marcada como cancelada y la mesa se libera
                  de inmediato.
                </DialogDescription>
              </DialogHeader>
              <DialogFooter>
                <Button
                  variant="outline"
                  disabled={cancelOrderMutation.isPending}
                  onClick={() => setConfirmCancelOpen(false)}
                >
                  Volver
                </Button>
                <Button
                  variant="destructive"
                  disabled={cancelOrderMutation.isPending}
                  onClick={() => cancelOrderMutation.mutate()}
                >
                  {cancelOrderMutation.isPending ? "Cancelando…" : "Sí, cancelar orden"}
                </Button>
              </DialogFooter>
            </DialogContent>
          </Dialog>
        </div>
      )}
    </QueryState>
  );
}

interface SendToKitchenActionProps {
  order: OrderResponse;
  cartIsEmpty: boolean;
  isPending: boolean;
  onSend: () => void;
  onDeliverAllReady?: () => void;
  isDeliveringAll?: boolean;
}

/** Region de accion "Enviar a cocina": solo tiene sentido mientras la orden
 * sigue NEW, porque el backend rechaza el reenvio con 409 en cualquier otro
 * estado. Para los demas estados se explica que ya paso, sin ocultar nada. */
function SendToKitchenAction({
  order,
  cartIsEmpty,
  isPending,
  onSend,
  onDeliverAllReady,
  isDeliveringAll,
}: SendToKitchenActionProps) {
  if (order.status === "NEW") {
    return (
      <Button
        size="xl"
        className="flex-1"
        disabled={isPending || (order.items.length === 0 && cartIsEmpty)}
        onClick={onSend}
      >
        {isPending ? "Enviando…" : "Enviar a cocina"}
      </Button>
    );
  }

  const allItemsDelivered =
    order.items.length > 0 && order.items.every((it) => it.kitchenStatus === "DELIVERED");

  if (allItemsDelivered) {
    return (
      <Alert className="flex-1 border-neutral-300 bg-neutral-100/50 dark:border-neutral-700 dark:bg-neutral-900/50">
        <AlertTitle className="flex items-center gap-1.5 font-medium text-foreground">
          <CheckCircle2Icon className="size-4 text-emerald-600" />
          Pedido entregado en mesa
        </AlertTitle>
        <AlertDescription className="text-muted-foreground">
          Todos los productos fueron entregados a los comensales. La orden está lista para el cobro en caja.
        </AlertDescription>
      </Alert>
    );
  }

  if (order.status === "READY") {
    const readyItems = order.items.filter((it) => it.kitchenStatus === "READY");
    return (
      <div className="flex flex-1 flex-col gap-2.5 sm:flex-row sm:items-center">
        <Alert className="flex-1 border-emerald-500/30 bg-emerald-500/10 text-emerald-950 dark:text-emerald-100">
          <AlertTitle className="flex items-center gap-1.5 font-semibold text-emerald-800 dark:text-emerald-300">
            <CheckCircle2Icon className="size-4 text-emerald-600" />
            ¡Pedido listo para servir!
          </AlertTitle>
          <AlertDescription className="text-emerald-900/90 dark:text-emerald-200/90">
            Cocina completó todos los platillos. Ya pueden llevarse a la mesa de los comensales.
          </AlertDescription>
        </Alert>
        {readyItems.length > 0 && onDeliverAllReady && (
          <Button
            type="button"
            variant="outline"
            size="lg"
            disabled={isDeliveringAll}
            onClick={onDeliverAllReady}
            className="border-emerald-500/40 text-emerald-700 hover:bg-emerald-500/10 dark:text-emerald-300 sm:self-stretch"
          >
            {isDeliveringAll ? "Entregando…" : "Entregar todo"}
          </Button>
        )}
      </div>
    );
  }

  if (order.status === "SENT") {
    return (
      <Alert className="flex-1">
        <AlertTitle>Ya está en cocina</AlertTitle>
        <AlertDescription>
          {order.sentToKitchenAt
            ? `Se envió a cocina a las ${formatShortTime(order.sentToKitchenAt)}.`
            : "Esta orden ya fue enviada a cocina."}
        </AlertDescription>
      </Alert>
    );
  }

  return (
    <Alert variant="destructive" className="flex-1">
      <AlertTitle>Orden cerrada</AlertTitle>
      <AlertDescription>
        {order.status === "PAID" ? "Esta orden ya fue pagada." : "Esta orden fue cancelada."}
      </AlertDescription>
    </Alert>
  );
}

function OrderSkeleton() {
  return (
    <div className="flex flex-1 flex-col gap-3 px-[18px] py-6 sm:px-7">
      <Skeleton className="h-9 w-64" />
      {Array.from({ length: 5 }).map((_, index) => (
        <Skeleton key={index} className="h-11 w-full" />
      ))}
    </div>
  );
}
