"use client";

import { useEffect, useState } from "react";
import QRCode from "qrcode";
import { QrCodeIcon, PrinterIcon, XIcon, SparklesIcon } from "lucide-react";

import { Button } from "@/components/ui/button";
import { useSession } from "@/hooks/use-session";
import type { TableResponse } from "@/lib/types";

interface PrintTablesQrDialogProps {
  tables: TableResponse[];
}

interface TableQrData {
  tableNumber: number;
  capacity: number;
  qrDataUrl: string;
  url: string;
}

export function PrintTablesQrDialog({ tables }: PrintTablesQrDialogProps) {
  const [isOpen, setIsOpen] = useState(false);
  const [qrList, setQrList] = useState<TableQrData[]>([]);
  const [isGenerating, setIsGenerating] = useState(false);
  const { user } = useSession();

  const slug = user?.slug ?? "menu";
  const restaurantName = user?.restaurantName ?? "RestoHub";

  useEffect(() => {
    if (!isOpen) return;

    let active = true;
    async function generateQrs() {
      setIsGenerating(true);
      const origin = typeof window !== "undefined" ? window.location.origin : "https://restohub.azurewebsites.net";
      const activeTables = tables.filter((t) => t.active).sort((a, b) => a.number - b.number);

      const generated: TableQrData[] = [];
      for (const table of activeTables) {
        const tableUrl = `${origin}/m/${slug}/${table.number}`;
        try {
          const dataUrl = await QRCode.toDataURL(tableUrl, {
            width: 280,
            margin: 1.5,
            color: {
              dark: "#0f172a",
              light: "#ffffff",
            },
          });
          generated.push({
            tableNumber: table.number,
            capacity: table.capacity,
            qrDataUrl: dataUrl,
            url: tableUrl,
          });
        } catch {
          // Si falla, se omite
        }
      }

      if (active) {
        setQrList(generated);
        setIsGenerating(false);
      }
    }

    generateQrs();
    return () => {
      active = false;
    };
  }, [isOpen, tables, slug]);

  function handlePrint() {
    window.print();
  }

  return (
    <>
      <Button
        variant="outline"
        size="sm"
        onClick={() => setIsOpen(true)}
        className="flex items-center gap-1.5 border-amber-500/30 text-amber-500 hover:text-amber-400 hover:border-amber-500 hover:bg-amber-500/10 active:scale-95 transition-all"
      >
        <QrCodeIcon className="w-4 h-4 text-amber-500" />
        Imprimir QR de Mesas
      </Button>

      {isOpen && (
        <div className="fixed inset-0 z-50 bg-neutral-950/80 backdrop-blur-sm flex items-center justify-center p-4">
          {/* ESTILOS DE IMPRESIÓN PARA HOJA DE TARJETAS 2x2 */}
          <style>{`
            @media print {
              body * {
                visibility: hidden !important;
              }
              #tables-qr-printable, #tables-qr-printable * {
                visibility: visible !important;
              }
              #tables-qr-printable {
                position: absolute !important;
                left: 0 !important;
                top: 0 !important;
                width: 100% !important;
                margin: 0 !important;
                padding: 10mm !important;
                background: white !important;
                color: black !important;
                display: grid !important;
                grid-template-columns: 1fr 1fr !important;
                gap: 12mm !important;
              }
              .table-qr-card {
                page-break-inside: avoid !important;
                border: 1.5px dashed #94a3b8 !important;
                border-radius: 12px !important;
                padding: 16px !important;
                text-align: center !important;
                background: white !important;
                color: black !important;
                display: flex !important;
                flex-direction: column !important;
                align-items: center !important;
                justify-content: space-between !important;
              }
              .no-print {
                display: none !important;
              }
            }
          `}</style>

          <div className="bg-neutral-900 border border-neutral-800 rounded-3xl max-w-4xl w-full max-h-[90vh] flex flex-col shadow-2xl overflow-hidden">
            {/* CABECERA MODAL */}
            <div className="p-5 border-b border-neutral-800 flex items-center justify-between no-print">
              <div>
                <div className="flex items-center gap-2">
                  <span className="text-xs font-bold text-amber-500 uppercase tracking-wider">
                    Salón & Servicio
                  </span>
                  <span className="text-neutral-600 text-xs">•</span>
                  <span className="text-xs text-neutral-400">
                    {qrList.length} mesas activas
                  </span>
                </div>
                <h2 className="text-lg font-bold text-neutral-100 flex items-center gap-2 mt-0.5">
                  <QrCodeIcon className="w-5 h-5 text-amber-400" />
                  Tarjetas QR para Mesas de {restaurantName}
                </h2>
                <p className="text-xs text-neutral-400 mt-1">
                  Imprime y recorta estas tarjetas para colocarlas en los acrílicos de tu restaurante.
                </p>
              </div>

              <div className="flex items-center gap-2">
                <Button
                  onClick={handlePrint}
                  disabled={isGenerating || qrList.length === 0}
                  className="bg-amber-500 hover:bg-amber-400 text-neutral-950 font-bold text-xs"
                >
                  <PrinterIcon className="w-3.5 h-3.5 mr-1.5" />
                  Mandar a Imprimir (A4 / Carta)
                </Button>
                <button
                  onClick={() => setIsOpen(false)}
                  className="w-8 h-8 rounded-full bg-neutral-800 text-neutral-400 hover:text-neutral-100 flex items-center justify-center transition-colors"
                >
                  <XIcon className="w-4 h-4" />
                </button>
              </div>
            </div>

            {/* VISTA PREVIA DE TARJETAS */}
            <div className="flex-1 overflow-y-auto p-6 bg-neutral-950/60">
              {isGenerating ? (
                <div className="text-center py-16 text-neutral-400">
                  <SparklesIcon className="w-8 h-8 mx-auto text-amber-400 animate-spin mb-3" />
                  <p className="text-sm font-semibold">Generando códigos QR de alta resolución...</p>
                </div>
              ) : qrList.length === 0 ? (
                <div className="text-center py-16 text-neutral-500">
                  <p className="text-sm">No hay mesas activas registradas para generar códigos QR.</p>
                </div>
              ) : (
                <div id="tables-qr-printable" className="grid grid-cols-1 md:grid-cols-2 gap-6">
                  {qrList.map((item) => (
                    <div
                      key={item.tableNumber}
                      className="table-qr-card bg-neutral-900 border-2 border-dashed border-neutral-700/80 rounded-2xl p-6 text-center flex flex-col items-center justify-between shadow-md"
                    >
                      {/* HEADER TARJETA */}
                      <div className="w-full pb-3 border-b border-neutral-800/80">
                        <span className="text-[10px] tracking-widest uppercase font-extrabold text-amber-500 block">
                          {restaurantName}
                        </span>
                        <h3 className="text-2xl font-black text-neutral-100 tracking-tight mt-0.5">
                          MESA {item.tableNumber}
                        </h3>
                        <span className="text-[11px] text-neutral-400">
                          Capacidad: {item.capacity} personas
                        </span>
                      </div>

                      {/* CÓDIGO QR */}
                      <div className="my-4 p-3 bg-white rounded-xl shadow-inner border border-neutral-200">
                        {/* eslint-disable-next-line @next/next/no-img-element */}
                        <img
                          src={item.qrDataUrl}
                          alt={`QR Mesa ${item.tableNumber}`}
                          className="w-44 h-44 object-contain"
                        />
                      </div>

                      {/* INSTRUCCIONES COMENSAL */}
                      <div className="w-full pt-3 border-t border-neutral-800/80 space-y-1 text-center">
                        <p className="text-xs font-bold text-neutral-200">
                          📱 Escanea con tu celular para ordenar
                        </p>
                        <p className="text-[10px] text-neutral-400 leading-tight">
                          Elige tus platillos y envíalos directo a preparación en cocina.
                        </p>
                        <p className="text-[9px] font-mono text-neutral-500 pt-1 truncate max-w-[240px] mx-auto">
                          {item.url}
                        </p>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>

            {/* FOOTER MODAL */}
            <div className="p-4 border-t border-neutral-800 bg-neutral-900 flex items-center justify-between text-xs text-neutral-500 no-print">
              <span>Consejo: Imprime en hoja opalina o cartulina gruesa de 200g para mayor durabilidad.</span>
              <Button
                variant="outline"
                size="sm"
                onClick={() => setIsOpen(false)}
                className="text-xs border-neutral-700 text-neutral-300"
              >
                Cerrar
              </Button>
            </div>
          </div>
        </div>
      )}
    </>
  );
}
