"use client";

import { useState } from "react";
import { toast } from "sonner";

import { Button } from "@/components/ui/button";
import { Separator } from "@/components/ui/separator";
import { Money } from "@/components/shared/money";
import { StatusBadge } from "@/components/shared/status-badge";
import { endpoints } from "@/lib/endpoints";
import { formatShortTime } from "@/lib/format";
import type { TicketResponse } from "@/lib/types";
import { PAYMENT_METHOD_LABELS } from "@/lib/types";

interface TicketSummaryProps {
  ticket: TicketResponse;
}

/**
 * Resumen de un ticket ya emitido, con boton para descargar su PDF. Se usa
 * tanto en caja (justo despues de cobrar, en checkout-panel.tsx) como en el
 * historial de ventas (al revisar un ticket ya cerrado), asi que la
 * descarga vive aqui una sola vez en lugar de duplicarse entre pantallas.
 *
 * Cifras siempre autoritativas del servidor: este componente solo formatea
 * lo que trae `ticket`, nunca recalcula nada.
 */
export function TicketSummary({ ticket }: TicketSummaryProps) {
  const [isDownloading, setIsDownloading] = useState(false);

  async function handleDownloadPdf() {
    setIsDownloading(true);
    try {
      // fetch directo con credenciales: la ruta necesita la cookie httpOnly,
      // y una descarga por window.open no la reenviaria de forma confiable a
      // traves del proxy. El blob se convierte en un object URL efimero solo
      // para disparar la descarga.
      const response = await fetch(endpoints.tickets.pdf(ticket.id), {
        credentials: "include",
      });
      if (!response.ok) {
        throw new Error("El servidor no pudo generar el PDF del ticket.");
      }

      const blob = await response.blob();
      const url = URL.createObjectURL(blob);
      const link = document.createElement("a");
      link.href = url;
      link.download = `ticket-${ticket.folio}.pdf`;
      document.body.appendChild(link);
      link.click();
      link.remove();
      URL.revokeObjectURL(url);
    } catch {
      toast.error("No se pudo descargar el PDF del ticket.");
    } finally {
      setIsDownloading(false);
    }
  }

  return (
    <div className="flex flex-col gap-3 rounded-md border bg-card p-4">
      <div className="flex items-center justify-between gap-3">
        <div>
          <p className="eyebrow">Ticket · {formatShortTime(ticket.closedAt)}</p>
          <p className="font-mono text-[15px]">{ticket.folio}</p>
        </div>
        <StatusBadge kind="payment" status={ticket.paymentMethod} />
      </div>

      <Separator />

      <div className="flex flex-col gap-1.5 text-sm">
        <div className="flex items-center justify-between">
          <span className="text-muted-foreground">Subtotal</span>
          <Money amount={ticket.subtotal} className="font-mono" />
        </div>
        <div className="flex items-center justify-between">
          <span className="text-muted-foreground">Propina ({ticket.tipPercent}%)</span>
          <Money amount={ticket.tip} className="font-mono" />
        </div>
      </div>

      <div>
        <p className="eyebrow">Total cobrado</p>
        <Money amount={ticket.total} className="block font-display text-[34px] leading-none" />
      </div>

      <div className="flex flex-col gap-2 text-sm">
        {ticket.payments?.map((payment, index) => (
          <div key={index} className="flex justify-between gap-3">
            <span>{PAYMENT_METHOD_LABELS[payment.method]} · recibido <Money amount={payment.receivedAmount} /></span>
            <Money amount={payment.amount} />
          </div>
        ))}
        <div className="flex justify-between"><span>Cambio</span><Money amount={ticket.change ?? 0} /></div>
      </div>
      <Button variant="outline" disabled={isDownloading} onClick={handleDownloadPdf}>
        {isDownloading ? "Descargando…" : "Descargar ticket en PDF"}
      </Button>
    </div>
  );
}
