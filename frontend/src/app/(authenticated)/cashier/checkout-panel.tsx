"use client";

import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";

import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Skeleton } from "@/components/ui/skeleton";
import { Money } from "@/components/shared/money";
import { QueryState } from "@/components/shared/query-state";
import { StatusBadge } from "@/components/shared/status-badge";
import { TicketSummary } from "@/components/shared/ticket-summary";
import { ApiError, api } from "@/lib/api";
import { endpoints } from "@/lib/endpoints";
import { formatCurrency, formatRoundedCurrency, formatShortTime } from "@/lib/format";
import { queryKeys } from "@/lib/query-keys";
import { cn } from "@/lib/utils";
import {
  PAYMENT_METHOD_LABELS,
  type CheckoutRequest,
  type OrderResponse,
  type PaymentMethod,
  type TableResponse,
  type TicketResponse,
} from "@/lib/types";

/** Porcentajes de propina sugeridos; el cajero puede escribir cualquier otro
 * valor con el boton "Otro %". */
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
 * Las dos columnas derechas de caja: el ticket con propina y metodo de pago, y
 * el resumen con el total y el boton de cobro. Se renderizan como hermanos
 * para que ocupen dos celdas del grid de `CashierView`.
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
  const [isCustomTip, setIsCustomTip] = useState(false);
  const [paymentMethod, setPaymentMethod] = useState<PaymentMethod | null>(null);
  const [cashReceived, setCashReceived] = useState<number | null>(null);
  const [customCash, setCustomCash] = useState("");
  const [isCustomCash, setIsCustomCash] = useState(false);
  const [ticket, setTicket] = useState<TicketResponse | null>(null);

  const orderQuery = useQuery({
    queryKey: queryKeys.orders.detail(orderId),
    queryFn: () => api.get<OrderResponse>(endpoints.orders.detail(orderId)),
  });
  const tablesQuery = useQuery({
    queryKey: queryKeys.tables.all(),
    queryFn: () => api.get<TableResponse[]>(endpoints.tables.list()),
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

  function selectTipPreset(percent: number) {
    setTipPercent(percent);
    setCustomTip("");
    setIsCustomTip(false);
  }

  function handleCustomTipChange(value: string) {
    setCustomTip(value);
    const parsed = Number(value);
    if (value !== "" && Number.isFinite(parsed) && parsed >= 0) {
      setTipPercent(parsed);
    }
  }

  function handleCustomCashChange(value: string) {
    setCustomCash(value);
    const parsed = Number(value);
    setCashReceived(value !== "" && Number.isFinite(parsed) ? parsed : null);
  }

  const order = orderQuery.data;
  const table = tablesQuery.data?.find((candidate) => candidate.id === order?.tableId);
  const subtotalPreview = order?.total ?? 0;
  const tipPreview = (subtotalPreview * tipPercent) / 100;
  const totalPreview = subtotalPreview + tipPreview;
  const isClosed = order?.status === "PAID" || order?.status === "CANCELED";
  const showCheckoutControls = Boolean(order) && !isClosed && !ticket;

  return (
    <>
      <section className="flex flex-col px-[18px] py-6 sm:px-7 sm:pt-6 sm:pb-7">
        <QueryState
          isLoading={orderQuery.isLoading}
          error={orderQuery.error}
          isEmpty={!order}
          emptyMessage="No se encontró la orden."
          skeleton={
            <div className="flex flex-col gap-3">
              <Skeleton className="h-10 w-72" />
              {Array.from({ length: 4 }).map((_, index) => (
                <Skeleton key={index} className="h-12 w-full" />
              ))}
            </div>
          }
        >
          {order && (
            <>
              <header className="flex flex-wrap items-end justify-between gap-4 border-b pb-[18px]">
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

              <div className="flex flex-col pt-1.5 pb-5">
                {order.items.map((item) => (
                  <div
                    key={item.id}
                    className="flex items-center gap-3.5 border-b border-dotted border-border-strong py-[13px]"
                  >
                    <span className="w-8 font-mono text-[13px] text-muted-foreground">
                      {item.quantity}×
                    </span>
                    <span className="flex-1 text-base">{item.productName}</span>
                    <Money
                      amount={item.unitPrice}
                      className="hidden font-mono text-[13px] text-muted-foreground sm:inline"
                    />
                    <Money
                      amount={item.unitPrice * item.quantity}
                      className="w-24 text-right text-base"
                    />
                  </div>
                ))}
              </div>

              {isClosed && (
                <Alert variant={order.status === "CANCELED" ? "destructive" : "default"}>
                  <AlertTitle>
                    {order.status === "PAID" ? "Orden ya cobrada" : "Orden cancelada"}
                  </AlertTitle>
                  <AlertDescription>
                    {order.status === "PAID"
                      ? "Esta orden ya fue pagada anteriormente y no admite un nuevo cobro."
                      : "Esta orden fue cancelada y no admite cobro."}
                  </AlertDescription>
                </Alert>
              )}

              {showCheckoutControls && (
                <div className="flex flex-col gap-[22px]">
                  <div className="flex flex-col gap-2.5">
                    <p className="eyebrow">Propina</p>
                    <div className="flex flex-wrap gap-2.5">
                      {TIP_PRESETS.map((percent) => (
                        <TipButton
                          key={percent}
                          label={`${percent}%`}
                          isSelected={!isCustomTip && tipPercent === percent}
                          onClick={() => selectTipPreset(percent)}
                        />
                      ))}
                      <TipButton
                        label="Otro %"
                        isSelected={isCustomTip}
                        onClick={() => setIsCustomTip(true)}
                      />
                    </div>
                    {isCustomTip && (
                      <Input
                        autoFocus
                        inputMode="decimal"
                        placeholder="Porcentaje de propina"
                        value={customTip}
                        onChange={(event) => handleCustomTipChange(event.target.value)}
                        className="max-w-[220px] font-mono"
                      />
                    )}
                  </div>

                  <div className="flex flex-col gap-2.5">
                    <p className="eyebrow">Método de pago</p>
                    <div className="grid grid-cols-2 gap-2.5 sm:grid-cols-3 xl:grid-cols-5">
                      {PAYMENT_METHODS.map((method) => (
                        <button
                          key={method}
                          type="button"
                          onClick={() => {
                            setPaymentMethod(method);
                            if (method !== "CASH") {
                              setCashReceived(null);
                              setIsCustomCash(false);
                              setCustomCash("");
                            }
                          }}
                          aria-pressed={paymentMethod === method}
                          className={cn(
                            "focus-sala inline-flex h-[66px] items-center justify-center rounded-md border px-2 text-center text-[15px]",
                            paymentMethod === method
                              ? "border-brand bg-brand-tint font-semibold text-foreground"
                              : "border-border text-muted-foreground hover:text-foreground",
                          )}
                        >
                          {PAYMENT_METHOD_LABELS[method]}
                        </button>
                      ))}
                    </div>
                  </div>
                </div>
              )}
            </>
          )}
        </QueryState>
      </section>

      <aside className="flex flex-col justify-between gap-6 bg-surface-alt px-[18px] py-6 sm:px-6 xl:border-l">
        {order && (
          <>
            <div className="flex flex-col gap-4">
              {ticket ? (
                <TicketSummary ticket={ticket} />
              ) : (
                <>
                  <div className="flex justify-between text-[15px] text-muted-foreground">
                    <span>Subtotal</span>
                    <Money amount={subtotalPreview} className="font-mono text-foreground" />
                  </div>
                  <div className="flex justify-between text-[15px] text-muted-foreground">
                    <span>Propina {tipPercent}%</span>
                    <Money amount={tipPreview} className="font-mono text-foreground" />
                  </div>
                  <div className="h-px bg-border" />
                  <div>
                    <p className="eyebrow">Total a cobrar</p>
                    <Money
                      amount={totalPreview}
                      className="mt-1.5 block font-display text-[46px] leading-[0.95] text-brand-strong sm:text-[62px]"
                    />
                  </div>

                  {paymentMethod === "CASH" && (
                    <CashBlock
                      total={totalPreview}
                      received={cashReceived}
                      isCustom={isCustomCash}
                      customValue={customCash}
                      onSelect={(amount) => {
                        setCashReceived(amount);
                        setIsCustomCash(false);
                        setCustomCash("");
                      }}
                      onCustom={() => setIsCustomCash(true)}
                      onCustomChange={handleCustomCashChange}
                    />
                  )}
                </>
              )}
            </div>

            {showCheckoutControls && (
              <div className="flex flex-col gap-3">
                <Button
                  variant="brand"
                  className="h-[74px] text-xl font-semibold"
                  disabled={!paymentMethod || checkoutMutation.isPending}
                  onClick={() => checkoutMutation.mutate()}
                >
                  {checkoutMutation.isPending
                    ? "Cobrando…"
                    : `Cobrar ${formatCurrency(totalPreview)}`}
                </Button>
                <p className="text-center text-xs text-muted-foreground">
                  El servidor confirma el monto e imprime el ticket {order.folio}.
                </p>
              </div>
            )}
          </>
        )}
      </aside>
    </>
  );
}

function TipButton({
  label,
  isSelected,
  onClick,
}: {
  label: string;
  isSelected: boolean;
  onClick: () => void;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      aria-pressed={isSelected}
      className={cn(
        "focus-sala inline-flex h-[58px] min-w-[72px] flex-1 items-center justify-center rounded-md border font-mono text-[17px]",
        isSelected
          ? "border-brand bg-brand font-semibold text-brand-foreground"
          : "border-border text-muted-foreground hover:text-foreground",
      )}
    >
      {label}
    </button>
  );
}

interface CashBlockProps {
  total: number;
  received: number | null;
  isCustom: boolean;
  customValue: string;
  onSelect: (amount: number) => void;
  onCustom: () => void;
  onCustomChange: (value: string) => void;
}

/**
 * Bloque de efectivo: solo aparece con metodo Efectivo. Los montos sugeridos
 * son el total redondeado hacia arriba a la siguiente centena y a la siguiente
 * unidad de mil — los dos billetes con los que se paga en la practica.
 *
 * El cambio es una ayuda de mostrador, no un dato del cobro: el backend no
 * recibe el efectivo entregado, solo el metodo y el porcentaje de propina.
 */
function CashBlock({
  total,
  received,
  isCustom,
  customValue,
  onSelect,
  onCustom,
  onCustomChange,
}: CashBlockProps) {
  const nextHundred = Math.ceil(total / 100) * 100;
  const nextThousand = Math.ceil(total / 1000) * 1000;
  const suggestions = [...new Set([nextHundred, nextThousand])].filter(
    (amount) => amount > 0,
  );
  const change = received === null ? null : received - total;

  return (
    <div className="flex flex-col gap-3 rounded-md border bg-card p-4">
      <p className="eyebrow">Efectivo recibido</p>
      <div className="flex gap-2">
        {suggestions.map((amount) => (
          <button
            key={amount}
            type="button"
            onClick={() => onSelect(amount)}
            aria-pressed={!isCustom && received === amount}
            className={cn(
              "focus-sala inline-flex h-[46px] flex-1 items-center justify-center rounded-sm border font-mono text-[15px]",
              !isCustom && received === amount
                ? "border-brand bg-brand-tint text-foreground"
                : "border-border text-muted-foreground hover:text-foreground",
            )}
          >
            {formatRoundedCurrency(amount)}
          </button>
        ))}
        <button
          type="button"
          onClick={onCustom}
          aria-pressed={isCustom}
          className={cn(
            "focus-sala inline-flex h-[46px] flex-1 items-center justify-center rounded-sm border border-dashed text-sm",
            isCustom
              ? "border-brand text-foreground"
              : "border-border-strong text-muted-foreground hover:text-foreground",
          )}
        >
          Otro
        </button>
      </div>

      {isCustom && (
        <Input
          autoFocus
          inputMode="decimal"
          placeholder="Monto recibido"
          value={customValue}
          onChange={(event) => onCustomChange(event.target.value)}
          className="font-mono"
        />
      )}

      <div className="flex justify-between text-sm text-muted-foreground">
        <span>Cambio</span>
        {change === null ? (
          <span className="font-mono text-base text-foreground">—</span>
        ) : (
          <Money
            amount={change}
            className={cn(
              "font-mono text-base",
              change < 0 ? "text-destructive" : "text-foreground",
            )}
          />
        )}
      </div>
    </div>
  );
}
