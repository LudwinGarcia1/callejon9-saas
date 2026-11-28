"use client";

import { useRef, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { QrCodeIcon } from "lucide-react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { MercadoPagoQrModal } from "@/components/shared/mercadopago-qr-modal";
import { Money } from "@/components/shared/money";
import { QueryState } from "@/components/shared/query-state";
import { TicketSummary } from "@/components/shared/ticket-summary";
import { ApiError, api } from "@/lib/api";
import { endpoints } from "@/lib/endpoints";
import { queryKeys } from "@/lib/query-keys";
import { paymentBalance, splitEqually } from "@/lib/split-payments";
import { PAYMENT_METHOD_LABELS, type CheckoutRequest, type OrderResponse, type PaymentRequest, type TicketResponse } from "@/lib/types";

export function CheckoutPanel({ orderId }: { orderId: string }) {
  const client = useQueryClient();
  const [tipPercent, setTipPercent] = useState(0);
  const [payments, setPayments] = useState<PaymentRequest[]>([]);
  const [parts, setParts] = useState(2);
  const [ticket, setTicket] = useState<TicketResponse | null>(null);
  const [isMpModalOpen, setIsMpModalOpen] = useState(false);
  const submitting = useRef(false);
  const query = useQuery({ queryKey: queryKeys.orders.detail(orderId), queryFn: () => api.get<OrderResponse>(endpoints.orders.detail(orderId)) });
  const subtotal = query.data?.total ?? 0;
  const tip = Math.round(subtotal * tipPercent) / 100;
  const total = Math.round((subtotal + tip) * 100) / 100;
  const balance = paymentBalance(payments, total);
  const mutation = useMutation({
    mutationFn: () => api.post<TicketResponse>(endpoints.orders.checkout(orderId), { payments, tipPercent } satisfies CheckoutRequest),
    onSuccess: (result) => {
      setTicket(result);
      for (const key of [queryKeys.orders.all(), queryKeys.tables.all(), queryKeys.sales.all(), queryKeys.analytics.all()]) client.invalidateQueries({ queryKey: key });
      toast.success(`Cobro registrado. Ticket ${result.folio}.`);
    },
    onError: (error) => toast.error(error instanceof ApiError ? error.message : "No se pudo registrar el cobro."),
    onSettled: () => { submitting.current = false; },
  });
  function charge() {
    if (submitting.current || !balance.valid || !Number.isFinite(tipPercent) || tipPercent < 0 || tipPercent > 100) return;
    submitting.current = true;
    mutation.mutate();
  }
  function update(index: number, patch: Partial<PaymentRequest>) {
    setPayments((current) => current.map((payment, i) => i === index ? { ...payment, ...patch } : payment));
  }
  const closed = query.data?.status === "PAID" || query.data?.status === "CANCELED";
  return <>
    <section className="flex flex-col gap-5 px-[18px] py-6 sm:px-7">
      <QueryState isLoading={query.isLoading} error={query.error} isEmpty={!query.data}>
        {query.data && <>
          <header><p className="eyebrow">{query.data.folio}</p><h1 className="font-display text-3xl">Cobrar cuenta</h1></header>
          {query.data.items.map((item) => <div key={item.id} className="flex justify-between border-b py-3"><span>{item.quantity} × {item.productName}</span><Money amount={item.unitPrice * item.quantity} /></div>)}
          {closed && !ticket && <p role="status">La orden está cerrada y no admite cobro.</p>}
          {!closed && !ticket && <fieldset disabled={mutation.isPending} className="flex flex-col gap-4">
            <legend className="eyebrow">Pagos de la cuenta</legend>
            <label>Propina (%)<Input type="number" min="0" max="100" step="0.01" value={tipPercent} onChange={(e) => setTipPercent(Number(e.target.value))} /></label>
            <div className="flex flex-wrap gap-2">
              <Button variant="outline" onClick={() => setPayments([{ method: "CASH", amount: total }])}>Pago único exacto</Button>
              <label>Personas<Input aria-label="Personas" type="number" min="1" max="50" value={parts} onChange={(e) => setParts(Number(e.target.value))} /></label>
              <Button variant="outline" disabled={!Number.isInteger(parts) || parts < 1 || parts > 50 || parts > Math.round(total * 100)} onClick={() => setPayments(splitEqually(total, parts))}>Dividir en partes iguales</Button>
            </div>
            {payments.map((payment, index) => (
              <div key={index} className="flex flex-col gap-2 rounded-md border p-3">
                <div className="flex flex-wrap items-end gap-2">
                  <label className="flex-1">
                    Método {index + 1}
                    <select
                      aria-label={`Método ${index + 1}`}
                      value={payment.method}
                      className="h-10 w-full rounded-md border bg-background px-2"
                      onChange={(e) => update(index, { method: e.target.value as PaymentRequest["method"] })}
                    >
                      {(["CASH", "CARD", "TRANSFER", "MERCADOPAGO"] as const).map((method) => (
                        <option key={method} value={method}>
                          {PAYMENT_METHOD_LABELS[method]}
                        </option>
                      ))}
                    </select>
                  </label>
                  <label className="flex-1">
                    Monto recibido {index + 1}
                    <Input
                      aria-label={`Monto recibido ${index + 1}`}
                      type="number"
                      min="0.01"
                      max="99999999.99"
                      step="0.01"
                      value={payment.amount || ""}
                      onChange={(e) => update(index, { amount: Number(e.target.value) })}
                    />
                  </label>
                  <Button
                    variant="outline"
                    aria-label={`Quitar pago ${index + 1}`}
                    onClick={() => setPayments(payments.filter((_, i) => i !== index))}
                  >
                    Quitar
                  </Button>
                </div>

                {/* Explicación y acciones específicas por método */}
                {payment.method === "MERCADOPAGO" && (
                  <div className="mt-1 flex flex-wrap items-center justify-between gap-2 rounded-md border border-[#009ee3]/40 bg-[#009ee3]/10 p-2.5 text-xs text-[#009ee3]">
                    <div className="flex items-center gap-1.5 font-medium">
                      <QrCodeIcon className="size-4 shrink-0" />
                      <span>Pasarela digital oficial: Requiere cobro con QR dinámico o enlace de Checkout Pro.</span>
                    </div>
                    <Button
                      type="button"
                      size="sm"
                      className="h-7 text-xs bg-[#009ee3] text-white hover:bg-[#0081ba] font-semibold"
                      onClick={() => setIsMpModalOpen(true)}
                    >
                      Abrir Pasarela QR
                    </Button>
                  </div>
                )}
                {payment.method === "CASH" && (
                  <p className="text-xs text-muted-foreground">
                    💵 Efectivo en caja: Registra el monto recibido en mano; el sistema calcula el cambio y suma al arqueo del turno.
                  </p>
                )}
                {payment.method === "CARD" && (
                  <p className="text-xs text-muted-foreground">
                    💳 Terminal bancaria física: Cobra con la terminal física de tarjeta del restaurante y registra el comprobante.
                  </p>
                )}
                {payment.method === "TRANSFER" && (
                  <p className="text-xs text-muted-foreground">
                    📲 Transferencia SPEI: Verifica el comprobante en tu app bancaria y registra el cobro.
                  </p>
                )}
              </div>
            ))}
            <Button variant="outline" disabled={payments.length >= 50} onClick={() => setPayments([...payments, { method: "CASH", amount: balance.remaining }])}>Agregar pago por monto libre</Button>
            <p className="text-sm text-muted-foreground">Los montos incluyen propina. Si cambias la propina, ajusta los pagos. Solo el efectivo admite cambio.</p>
          </fieldset>}
        </>}
      </QueryState>
    </section>
    <aside className="flex flex-col gap-4 bg-surface-alt px-[18px] py-6 xl:border-l">
      {ticket ? <TicketSummary ticket={ticket} /> : query.data && <>
        <div className="flex justify-between"><span>Subtotal</span><Money amount={subtotal} /></div>
        <div className="flex justify-between"><span>Propina</span><Money amount={tip} /></div>
        <p className="eyebrow">Total a cobrar</p><Money amount={total} className="font-display text-5xl" />
        <div className="flex justify-between"><span>Falta cubrir</span><Money amount={balance.remaining} /></div>
        <div className="flex justify-between"><span>Cambio de efectivo</span><Money amount={balance.change} /></div>
        {balance.nonCashExcess && <p role="alert" className="text-destructive">Los pagos sin efectivo exceden el total.</p>}
        {mutation.error && <p role="alert" className="text-destructive">{mutation.error.message}</p>}
        {!closed && (
          <div className="flex flex-col gap-2">
            {payments.length === 1 && payments[0].method === "MERCADOPAGO" ? (
              <Button
                className="h-14 text-lg bg-[#009ee3] hover:bg-[#0081ba] text-white font-bold gap-2 shadow-sm"
                disabled={!balance.valid || !Number.isFinite(tipPercent) || tipPercent < 0 || tipPercent > 100 || mutation.isPending}
                onClick={() => setIsMpModalOpen(true)}
              >
                <QrCodeIcon className="size-5" />
                Cobrar con QR Mercado Pago
              </Button>
            ) : (
              <Button
                className="h-14 text-lg"
                disabled={!balance.valid || !Number.isFinite(tipPercent) || tipPercent < 0 || tipPercent > 100 || mutation.isPending}
                onClick={charge}
              >
                {mutation.isPending ? "Cobrando…" : "Confirmar cobro"}
              </Button>
            )}

            {!(payments.length === 1 && payments[0].method === "MERCADOPAGO") && (
              <Button
                type="button"
                variant="outline"
                className="h-12 border-[#009ee3]/60 text-[#009ee3] hover:bg-[#009ee3]/10 font-bold gap-2 text-sm shadow-sm"
                disabled={mutation.isPending}
                onClick={() => setIsMpModalOpen(true)}
              >
                <QrCodeIcon className="w-4 h-4 text-[#009ee3]" />
                Cobrar con QR Mercado Pago
              </Button>
            )}
          </div>
        )}
      </>}
    </aside>

    <MercadoPagoQrModal
      orderId={orderId}
      tipPercent={tipPercent}
      isOpen={isMpModalOpen}
      onClose={() => setIsMpModalOpen(false)}
      onPaid={() => {
        query.refetch();
        for (const key of [
          queryKeys.orders.all(),
          queryKeys.tables.all(),
          queryKeys.sales.all(),
          queryKeys.analytics.all(),
        ]) {
          client.invalidateQueries({ queryKey: key });
        }
      }}
    />
  </>;
}
