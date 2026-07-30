"use client";

import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";

import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Separator } from "@/components/ui/separator";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
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
  ProductResponse,
} from "@/lib/types";
import { ProductPicker, type CartLine } from "./product-picker";

interface OrderViewProps {
  orderId: string;
}

/**
 * Pantalla de una orden: sus productos con el total autoritativo del
 * servidor a la izquierda, el selector de productos por categoria y el
 * carrito local a la derecha. El carrito nunca se envia producto por
 * producto: se junta localmente y se confirma en un solo POST por lote.
 */
export function OrderView({ orderId }: OrderViewProps) {
  const queryClient = useQueryClient();
  const [cart, setCart] = useState<CartLine[]>([]);

  const orderQuery = useQuery({
    queryKey: queryKeys.orders.detail(orderId),
    queryFn: () => api.get<OrderResponse>(endpoints.orders.detail(orderId)),
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

  return (
    <QueryState
      isLoading={orderQuery.isLoading}
      error={orderQuery.error}
      isEmpty={!order}
      emptyMessage="No se encontro la orden."
    >
      {order && (
        <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
          <Card>
            <CardHeader className="flex flex-row items-start justify-between gap-2">
              <div>
                <CardTitle>Orden {order.folio}</CardTitle>
                <p className="text-sm text-muted-foreground">
                  {order.guestCount} comensales
                </p>
              </div>
              <StatusBadge kind="order" status={order.status} />
            </CardHeader>
            <CardContent className="flex flex-col gap-4">
              {order.items.length === 0 ? (
                <p className="text-sm text-muted-foreground">
                  Aun no se han agregado productos a esta orden.
                </p>
              ) : (
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>Producto</TableHead>
                      <TableHead>Cantidad</TableHead>
                      <TableHead>Precio</TableHead>
                      <TableHead>Subtotal</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {order.items.map((item) => (
                      <TableRow key={item.id}>
                        <TableCell>{item.productName}</TableCell>
                        <TableCell>{item.quantity}</TableCell>
                        <TableCell>
                          <Money amount={item.unitPrice} />
                        </TableCell>
                        <TableCell>
                          <Money amount={item.unitPrice * item.quantity} />
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              )}

              <Separator />

              <div className="flex items-center justify-between">
                <span className="font-medium">Total</span>
                <Money amount={order.total} className="text-xl font-semibold" />
              </div>

              <SendToKitchenAction
                order={order}
                cartIsEmpty={cart.length === 0}
                isPending={sendToKitchenMutation.isPending}
                onSend={() => sendToKitchenMutation.mutate()}
              />
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle>Agregar productos</CardTitle>
            </CardHeader>
            <CardContent>
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
            </CardContent>
          </Card>
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
        size="lg"
        className="w-full"
        disabled={isPending || (order.items.length === 0 && cartIsEmpty)}
        onClick={onSend}
      >
        {isPending ? "Enviando..." : "Enviar a cocina"}
      </Button>
    );
  }

  if (order.status === "SENT" || order.status === "READY") {
    return (
      <Alert>
        <AlertTitle>Ya esta en cocina</AlertTitle>
        <AlertDescription>
          {order.sentToKitchenAt
            ? `Se envio a cocina a las ${formatShortTime(order.sentToKitchenAt)}.`
            : "Esta orden ya fue enviada a cocina."}
        </AlertDescription>
      </Alert>
    );
  }

  return (
    <Alert variant="destructive">
      <AlertTitle>Orden cerrada</AlertTitle>
      <AlertDescription>
        {order.status === "PAID" ? "Esta orden ya fue pagada." : "Esta orden fue cancelada."}
      </AlertDescription>
    </Alert>
  );
}
