"use client";

import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";

import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
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
import { TicketSummary } from "@/components/shared/ticket-summary";
import { ApiError, api } from "@/lib/api";
import { endpoints } from "@/lib/endpoints";
import { queryKeys } from "@/lib/query-keys";
import {
  PAYMENT_METHOD_LABELS,
  type CheckoutRequest,
  type OrderResponse,
  type PaymentMethod,
  type TicketResponse,
} from "@/lib/types";

/** Porcentajes de propina sugeridos; el cajero puede escribir cualquier otro
 * valor en el campo "Otro %". */
const TIP_PRESETS = [0, 10, 15, 20];

const PAYMENT_METHODS: PaymentMethod[] = [
  "CASH",
  "CARD",
  "TRANSFER",
  "MIXED",
  "MERCADOPAGO",
];

interface CheckoutPanelProps {
  orderId: string;
}

/**
 * Detalle de una orden seleccionada en caja: sus productos, el control de
 * propina y metodo de pago, y tras cobrar, el ticket resultante con su PDF.
 *
 * La vista previa de propina/total es puramente cosmetica (aritmetica local
 * sobre `order.total`, que ya es el subtotal calculado por el servidor); en
 * cuanto el cobro se confirma, todo lo que se muestra viene de la respuesta
 * de POST /orders/{id}/checkout, nunca de este calculo local.
 */
export function CheckoutPanel({ orderId }: CheckoutPanelProps) {
  const queryClient = useQueryClient();
  const [tipPercent, setTipPercent] = useState(0);
  const [customTip, setCustomTip] = useState("");
  const [paymentMethod, setPaymentMethod] = useState<PaymentMethod | null>(null);
  const [ticket, setTicket] = useState<TicketResponse | null>(null);

  const orderQuery = useQuery({
    queryKey: queryKeys.orders.detail(orderId),
    queryFn: () => api.get<OrderResponse>(endpoints.orders.detail(orderId)),
  });

  const checkoutMutation = useMutation({
    mutationFn: () =>
      api.post<TicketResponse>(endpoints.orders.checkout(orderId), {
        paymentMethod: paymentMethod as PaymentMethod,
        tipPercent,
      } satisfies CheckoutRequest),
    onSuccess: (result) => {
      setTicket(result);
      queryClient.invalidateQueries({ queryKey: queryKeys.orders.all() });
      queryClient.invalidateQueries({ queryKey: queryKeys.orders.detail(orderId) });
      queryClient.invalidateQueries({ queryKey: queryKeys.tables.all() });
      toast.success(`Cobro registrado. Ticket ${result.folio}.`);
    },
    onError: (error) => {
      toast.error(
        error instanceof ApiError ? error.message : "No se pudo registrar el cobro.",
      );
    },
  });

  function handleTipPreset(percent: number) {
    setTipPercent(percent);
    setCustomTip("");
  }

  function handleCustomTipChange(value: string) {
    setCustomTip(value);
    const parsed = Number(value);
    if (value !== "" && Number.isFinite(parsed) && parsed >= 0) {
      setTipPercent(parsed);
    }
  }

  const order = orderQuery.data;
  const subtotalPreview = order?.total ?? 0;
  const tipPreview = (subtotalPreview * tipPercent) / 100;
  const totalPreview = subtotalPreview + tipPreview;

  return (
    <Card>
      <CardHeader>
        <CardTitle>{order ? `Orden ${order.folio}` : "Detalle de la orden"}</CardTitle>
      </CardHeader>
      <CardContent>
        <QueryState
          isLoading={orderQuery.isLoading}
          error={orderQuery.error}
          isEmpty={!order}
          emptyMessage="No se encontro la orden."
        >
          {order && (
            <div className="flex flex-col gap-6">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Producto</TableHead>
                    <TableHead>Cantidad</TableHead>
                    <TableHead>Subtotal</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {order.items.map((item) => (
                    <TableRow key={item.id}>
                      <TableCell>{item.productName}</TableCell>
                      <TableCell>{item.quantity}</TableCell>
                      <TableCell>
                        <Money amount={item.unitPrice * item.quantity} />
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>

              {ticket ? (
                <TicketSummary ticket={ticket} />
              ) : order.status === "PAID" ? (
                <Alert>
                  <AlertTitle>Orden ya cobrada</AlertTitle>
                  <AlertDescription>
                    Esta orden ya fue pagada anteriormente y no admite un nuevo cobro.
                  </AlertDescription>
                </Alert>
              ) : order.status === "CANCELED" ? (
                <Alert variant="destructive">
                  <AlertTitle>Orden cancelada</AlertTitle>
                  <AlertDescription>
                    Esta orden fue cancelada y no admite cobro.
                  </AlertDescription>
                </Alert>
              ) : (
                <div className="flex flex-col gap-4">
                  <Separator />

                  <div className="flex flex-col gap-2">
                    <Label>Propina</Label>
                    <div className="flex flex-wrap items-center gap-2">
                      {TIP_PRESETS.map((percent) => (
                        <Button
                          key={percent}
                          type="button"
                          size="sm"
                          variant={
                            tipPercent === percent && customTip === "" ? "default" : "outline"
                          }
                          onClick={() => handleTipPreset(percent)}
                        >
                          {percent}%
                        </Button>
                      ))}
                      <Input
                        placeholder="Otro %"
                        className="w-24"
                        inputMode="decimal"
                        value={customTip}
                        onChange={(event) => handleCustomTipChange(event.target.value)}
                      />
                    </div>
                  </div>

                  <div className="flex flex-col gap-2">
                    <Label>Metodo de pago</Label>
                    <div className="grid grid-cols-2 gap-2 sm:grid-cols-3">
                      {PAYMENT_METHODS.map((method) => (
                        <Button
                          key={method}
                          type="button"
                          size="lg"
                          variant={paymentMethod === method ? "default" : "outline"}
                          onClick={() => setPaymentMethod(method)}
                        >
                          {PAYMENT_METHOD_LABELS[method]}
                        </Button>
                      ))}
                    </div>
                  </div>

                  <Separator />

                  <div className="flex flex-col gap-1 text-sm">
                    <div className="flex items-center justify-between">
                      <span className="text-muted-foreground">Subtotal</span>
                      <Money amount={subtotalPreview} />
                    </div>
                    <div className="flex items-center justify-between">
                      <span className="text-muted-foreground">Propina ({tipPercent}%)</span>
                      <Money amount={tipPreview} />
                    </div>
                    <div className="flex items-center justify-between text-base font-semibold">
                      <span>Total estimado</span>
                      <Money amount={totalPreview} />
                    </div>
                    <p className="text-xs text-muted-foreground">
                      Vista previa de referencia; el monto final lo confirma el servidor al
                      cobrar.
                    </p>
                  </div>

                  <Button
                    size="lg"
                    disabled={!paymentMethod || checkoutMutation.isPending}
                    onClick={() => checkoutMutation.mutate()}
                  >
                    {checkoutMutation.isPending ? "Cobrando..." : "Cobrar"}
                  </Button>
                </div>
              )}
            </div>
          )}
        </QueryState>
      </CardContent>
    </Card>
  );
}
