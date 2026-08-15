"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
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
import { ApiError, api } from "@/lib/api";
import { endpoints } from "@/lib/endpoints";
import { formatShortTime } from "@/lib/format";
import { queryKeys } from "@/lib/query-keys";
import type {
  AddOrderItemsRequest,
  CategoryResponse,
  OrderResponse,
  OrderStatus,
  ProductResponse,
  TableResponse,
} from "@/lib/types";
import { ProductPicker, type CartLine } from "./product-picker";

interface OrderViewProps {
  orderId: string;
}

/** Estados en los que la orden todavia se puede cancelar: coincide con la
 * guarda de {@code OrderService.cancelOrder}, que solo rechaza PAID y
 * CANCELED. */
const CANCELABLE_ORDER_STATUSES = new Set<OrderStatus>(["NEW", "SENT", "READY"]);

/**
 * Pantalla de una orden. La orden manda y ocupa la columna ancha; el catalogo
 * es un panel de apoyo de 430px a la derecha. El carrito nunca se envia
 * producto por producto: se junta localmente y se confirma en un solo POST por
 * lote.
 */
export function OrderView({ orderId }: OrderViewProps) {
  const router = useRouter();
  const queryClient = useQueryClient();
  const [cart, setCart] = useState<CartLine[]>([]);
  const [confirmCancelOpen, setConfirmCancelOpen] = useState(false);

  const orderQuery = useQuery({
    queryKey: queryKeys.orders.detail(orderId),
    queryFn: () => api.get<OrderResponse>(endpoints.orders.detail(orderId)),
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
        items: lines.map((line) => ({
          productId: line.product.id,
          quantity: line.quantity,
        })),
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

  function addProductToCart(product: ProductResponse) {
    setCart((previous) => {
      const existing = previous.find((line) => line.product.id === product.id);
      if (existing) {
        return previous.map((line) =>
          line.product.id === product.id ? { ...line, quantity: line.quantity + 1 } : line,
        );
      }
      return [...previous, { product, quantity: 1 }];
    });
  }

  function incrementLine(productId: string) {
    setCart((previous) =>
      previous.map((line) =>
        line.product.id === productId ? { ...line, quantity: line.quantity + 1 } : line,
      ),
    );
  }

  function decrementLine(productId: string) {
    setCart((previous) =>
      previous.flatMap((line) => {
        if (line.product.id !== productId) {
          return [line];
        }
        if (line.quantity <= 1) {
          return [];
        }
        return [{ ...line, quantity: line.quantity - 1 }];
      }),
    );
  }

  function removeLine(productId: string) {
    setCart((previous) => previous.filter((line) => line.product.id !== productId));
  }

  const order = orderQuery.data;
  const table = tablesQuery.data?.find((candidate) => candidate.id === order?.tableId);
  const isCancelable = order ? CANCELABLE_ORDER_STATUSES.has(order.status) : false;

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
                      <Money
                        amount={item.unitPrice}
                        className="hidden font-mono text-[13px] text-muted-foreground sm:inline"
                      />
                      <Money
                        amount={item.unitPrice * item.quantity}
                        className="w-24 text-right text-[15px]"
                      />
                    </div>
                  ))}
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
            onCommit={() => addItemsMutation.mutate(cart)}
            isCommitting={addItemsMutation.isPending}
            disabled={order.status === "PAID" || order.status === "CANCELED"}
            alreadySentToKitchen={order.status === "SENT" || order.status === "READY"}
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
}

/** Region de accion "Enviar a cocina": solo tiene sentido mientras la orden
 * sigue NEW, porque el backend rechaza el reenvio con 409 en cualquier otro
 * estado. Para los demas estados se explica que ya paso, sin ocultar nada. */
function SendToKitchenAction({ order, cartIsEmpty, isPending, onSend }: SendToKitchenActionProps) {
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

  if (order.status === "SENT" || order.status === "READY") {
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
