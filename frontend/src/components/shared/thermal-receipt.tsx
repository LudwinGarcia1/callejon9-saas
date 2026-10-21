"use client";

import { PrinterIcon, XIcon } from "lucide-react";
import { Button } from "@/components/ui/button";
import { formatShortTime } from "@/lib/format";
import type { TicketResponse } from "@/lib/types";
import { PAYMENT_METHOD_LABELS } from "@/lib/types";

interface ThermalReceiptProps {
  ticket: TicketResponse;
  isOpen: boolean;
  onClose: () => void;
}

export function ThermalReceiptDialog({ ticket, isOpen, onClose }: ThermalReceiptProps) {
  if (!isOpen) return null;

  function handlePrint() {
    window.print();
  }

  return (
    <div className="fixed inset-0 z-50 bg-neutral-950/80 backdrop-blur-sm flex items-center justify-center p-4">
      {/* Estilos estrictos para impresion termica de 80mm */}
      <style>{`
        @media print {
          body * {
            visibility: hidden !important;
          }
          #thermal-receipt-printable, #thermal-receipt-printable * {
            visibility: visible !important;
          }
          #thermal-receipt-printable {
            position: absolute !important;
            left: 0 !important;
            top: 0 !important;
            width: 80mm !important;
            margin: 0 !important;
            padding: 4mm !important;
            background: white !important;
            color: black !important;
            font-family: 'Courier New', Courier, monospace !important;
            font-size: 11pt !important;
            line-height: 1.25 !important;
          }
          .no-print {
            display: none !important;
          }
        }
      `}</style>

      <div className="bg-neutral-900 border border-neutral-800 rounded-2xl max-w-sm w-full p-5 shadow-2xl flex flex-col gap-4">
        <div className="flex items-center justify-between no-print">
          <div className="flex items-center gap-2 text-neutral-100 font-bold text-sm">
            <PrinterIcon className="w-4 h-4 text-amber-400" />
            Vista Previa de Ticket Térmico (80mm)
          </div>
          <button
            onClick={onClose}
            className="w-7 h-7 rounded-full bg-neutral-800 text-neutral-400 hover:text-neutral-100 flex items-center justify-center"
          >
            <XIcon className="w-4 h-4" />
          </button>
        </div>

        {/* TICKET PAPEL 80MM */}
        <div
          id="thermal-receipt-printable"
          className="bg-neutral-50 text-neutral-900 font-mono text-[11px] p-5 rounded-lg border border-neutral-300 shadow-inner space-y-2 select-text"
        >
          <div className="text-center space-y-0.5 border-b border-dashed border-neutral-400 pb-2">
            <p className="font-extrabold text-[13px] tracking-wider uppercase">RESTOHUB · CALLEJÓN 9</p>
            <p className="text-[10px] text-neutral-600">Comprobante de Consumo</p>
            <p className="text-[10px]">Folio: <strong>{ticket.folio}</strong></p>
            <p className="text-[10px] text-neutral-500">{formatShortTime(ticket.closedAt)}</p>
          </div>

          <div className="border-b border-dashed border-neutral-400 py-2 space-y-1">
            <div className="flex justify-between font-bold text-[10px] uppercase border-b border-neutral-300 pb-1">
              <span>Cant. / Descrip.</span>
              <span>Importe</span>
            </div>

            {ticket.items && ticket.items.length > 0 ? (
              ticket.items.map((it, idx) => (
                <div key={idx} className="flex justify-between text-[10px]">
                  <span className="truncate pr-2">{it.quantity}x {it.productName}</span>
                  <span className="font-bold whitespace-nowrap">${(it.unitPrice * it.quantity).toFixed(2)}</span>
                </div>
              ))
            ) : (
              <div className="flex justify-between text-[10px]">
                <span>Consumo de alimentos y bebidas</span>
                <span className="font-bold">${ticket.subtotal.toFixed(2)}</span>
              </div>
            )}
          </div>

          <div className="space-y-1 text-[10px] border-b border-dashed border-neutral-400 pb-2">
            <div className="flex justify-between">
              <span>Subtotal:</span>
              <span>${ticket.subtotal.toFixed(2)}</span>
            </div>
            {ticket.tip > 0 && (
              <div className="flex justify-between">
                <span>Propina ({ticket.tipPercent}%):</span>
                <span>${ticket.tip.toFixed(2)}</span>
              </div>
            )}
            <div className="flex justify-between font-extrabold text-[13px] pt-1 border-t border-neutral-400">
              <span>TOTAL:</span>
              <span>${ticket.total.toFixed(2)} MXN</span>
            </div>
          </div>

          <div className="space-y-0.5 text-[10px] border-b border-dashed border-neutral-400 pb-2">
            <p className="font-bold text-[9px] uppercase text-neutral-600">Forma de Pago:</p>
            {ticket.payments?.map((p, i) => (
              <div key={i} className="flex justify-between">
                <span>{PAYMENT_METHOD_LABELS[p.method]}:</span>
                <span>${p.amount.toFixed(2)}</span>
              </div>
            ))}
            {(ticket.change ?? 0) > 0 && (
              <div className="flex justify-between font-bold text-neutral-800">
                <span>Cambio entregado:</span>
                <span>${(ticket.change ?? 0).toFixed(2)}</span>
              </div>
            )}
          </div>

          <div className="text-center pt-2 text-[9px] text-neutral-600 space-y-0.5">
            <p className="font-bold">¡GRACIAS POR SU PREFERENCIA!</p>
            <p>Sistema RestoHub POS</p>
          </div>
        </div>

        <div className="flex gap-2 no-print pt-2">
          <Button
            variant="outline"
            onClick={onClose}
            className="flex-1 text-xs border-neutral-700 text-neutral-300"
          >
            Cerrar
          </Button>
          <Button
            onClick={handlePrint}
            className="flex-1 text-xs bg-amber-500 hover:bg-amber-400 text-neutral-950 font-bold"
          >
            <PrinterIcon className="w-3.5 h-3.5 mr-1.5" />
            Imprimir (80mm)
          </Button>
        </div>
      </div>
    </div>
  );
}
