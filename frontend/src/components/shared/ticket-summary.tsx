"use client";

import { useState } from "react";
import { toast } from "sonner";
import { PrinterIcon } from "lucide-react";

import { Button } from "@/components/ui/button";
import { Separator } from "@/components/ui/separator";
import { Money } from "@/components/shared/money";
import { StatusBadge } from "@/components/shared/status-badge";
import { ThermalReceiptDialog } from "@/components/shared/thermal-receipt";
import { api } from "@/lib/api";
import { endpoints } from "@/lib/endpoints";
import { formatShortTime } from "@/lib/format";
import type { TicketResponse } from "@/lib/types";
import { PAYMENT_METHOD_LABELS } from "@/lib/types";

interface TicketSummaryProps {
  ticket: TicketResponse;
}

/**
 * Resumen de un ticket ya emitido, con boton para descargar su PDF y
 * para imprimir directamente en impresora térmica de 80mm. Se usa
 * tanto en caja (justo despues de cobrar, en checkout-panel.tsx) como en el
 * historial de ventas (al revisar un ticket ya cerrado).
 */
export function TicketSummary({ ticket }: TicketSummaryProps) {
  const [isDownloading, setIsDownloading] = useState(false);
  const [isThermalOpen, setIsThermalOpen] = useState(false);

  async function handleDownloadPdf() {
    setIsDownloading(true);
    try {
      const blob = await api.blob(endpoints.tickets.pdf(ticket.id));
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
    <>
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

        <div className="flex flex-col gap-2 pt-1">
          <Button
            variant="default"
            onClick={() => setIsThermalOpen(true)}
            className="w-full bg-amber-500 hover:bg-amber-400 text-neutral-950 font-bold"
          >
            <PrinterIcon className="w-4 h-4 mr-2" />
            Imprimir Ticket Térmico (80mm)
          </Button>

          <Button variant="outline" disabled={isDownloading} onClick={handleDownloadPdf}>
            {isDownloading ? "Descargando…" : "Descargar ticket en PDF"}
          </Button>
        </div>
      </div>

      <ThermalReceiptDialog
        ticket={ticket}
        isOpen={isThermalOpen}
        onClose={() => setIsThermalOpen(false)}
      />
    </>
  );
}
