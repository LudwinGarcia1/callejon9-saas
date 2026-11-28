"use client";

import { useEffect, useState } from "react";
import QRCode from "qrcode";
import { CheckCircle2Icon, ExternalLinkIcon, Loader2Icon, RefreshCwIcon, SparklesIcon, XIcon } from "lucide-react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Money } from "@/components/shared/money";
import { api } from "@/lib/api";
import { endpoints } from "@/lib/endpoints";
import type { PaymentStatusResponse, PreferenceResponse } from "@/lib/types";

interface MercadoPagoQrModalProps {
  orderId: string;
  tipPercent: number;
  isOpen: boolean;
  onClose: () => void;
  onPaid: () => void;
  publicSlug?: string;
  publicTableNumber?: string | number;
}

export function MercadoPagoQrModal({
  orderId,
  tipPercent,
  isOpen,
  onClose,
  onPaid,
  publicSlug,
  publicTableNumber,
}: MercadoPagoQrModalProps) {
  const [loading, setLoading] = useState(false);
  const [preference, setPreference] = useState<PreferenceResponse | null>(null);
  const [qrDataUrl, setQrDataUrl] = useState<string | null>(null);
  const [paid, setPaid] = useState(false);
  const [simulating, setSimulating] = useState(false);
  const [checking, setChecking] = useState(false);

  useEffect(() => {
    if (!isOpen || (!orderId && !(publicSlug && publicTableNumber))) {
      setPreference(null);
      setQrDataUrl(null);
      setPaid(false);
      return;
    }

    let isMounted = true;
    setLoading(true);

    const preferenceEndpoint = publicSlug && publicTableNumber
      ? endpoints.publicMenu.mercadopagoPreference(publicSlug, publicTableNumber)
      : endpoints.orders.mercadopagoPreference(orderId);

    api.post<PreferenceResponse>(preferenceEndpoint, { tipPercent })
      .then(async (pref) => {
        if (!isMounted) return;
        setPreference(pref);

        try {
          const url = await QRCode.toDataURL(pref.initPoint, {
            width: 260,
            margin: 1.5,
            color: {
              dark: "#009ee3", // Color institucional Mercado Pago
              light: "#ffffff",
            },
          });
          if (isMounted) setQrDataUrl(url);
        } catch (err) {
          console.error("Error generating MP QR:", err);
        }
      })
      .catch((err) => {
        toast.error("No se pudo iniciar el cobro con Mercado Pago");
        console.error(err);
      })
      .finally(() => {
        if (isMounted) setLoading(false);
      });

    return () => {
      isMounted = false;
    };
  }, [isOpen, orderId, tipPercent, publicSlug, publicTableNumber]);

  // Polling para detectar cuando el webhook o Mercado Pago apruebe la orden
  useEffect(() => {
    if (!isOpen || paid || !preference) return;

    const interval = setInterval(async () => {
      try {
        let res: PaymentStatusResponse;
        if (preference.isMock) {
          const statusEndpoint = publicSlug && publicTableNumber
            ? endpoints.publicMenu.paymentStatus(publicSlug, publicTableNumber)
            : endpoints.orders.paymentStatus(orderId);
          res = await api.get<PaymentStatusResponse>(statusEndpoint);
        } else {
          const syncEndpoint = publicSlug && publicTableNumber
            ? endpoints.publicMenu.mercadopagoSync(publicSlug, publicTableNumber)
            : endpoints.orders.mercadopagoSync(orderId);
          res = await api.post<PaymentStatusResponse>(syncEndpoint, {
            externalReference: preference.externalReference,
          });
        }

        if (res.paid) {
          setPaid(true);
          toast.success(`¡Pago recibido con Mercado Pago! Folio: ${res.ticketFolio || res.folio}`);
          setTimeout(() => {
            onPaid();
            onClose();
          }, 1800);
        }
      } catch {
        // Silencioso durante polling
      }
    }, 2500);

    return () => clearInterval(interval);
  }, [isOpen, paid, preference, orderId, publicSlug, publicTableNumber, onPaid, onClose]);

  async function handleManualCheck() {
    if (!preference || paid || checking) return;
    setChecking(true);
    try {
      const syncEndpoint = publicSlug && publicTableNumber
        ? endpoints.publicMenu.mercadopagoSync(publicSlug, publicTableNumber)
        : endpoints.orders.mercadopagoSync(orderId);
      const res = await api.post<PaymentStatusResponse>(syncEndpoint, {
        externalReference: preference.externalReference,
      });
      if (res.paid) {
        setPaid(true);
        toast.success(`¡Pago recibido con Mercado Pago! Folio: ${res.ticketFolio || res.folio}`);
        setTimeout(() => {
          onPaid();
          onClose();
        }, 1800);
      } else {
        toast.info("Aún no detectamos el pago aprobado en Mercado Pago. Si ya lo completaste, dale unos segundos.");
      }
    } catch {
      toast.error("No se pudo verificar el estado en Mercado Pago");
    } finally {
      setChecking(false);
    }
  }

  async function handleSimulate() {
    setSimulating(true);
    try {
      const simEndpoint = publicSlug && publicTableNumber
        ? endpoints.publicMenu.mercadopagoSimulate(publicSlug, publicTableNumber)
        : endpoints.orders.mercadopagoSimulate(orderId);

      const res = await api.post<PaymentStatusResponse>(simEndpoint, { tipPercent });
      if (res.paid) {
        setPaid(true);
        toast.success(`Cobro simulado con éxito. Ticket: ${res.ticketFolio}`);
        setTimeout(() => {
          onPaid();
          onClose();
        }, 1500);
      }
    } catch {
      toast.error("No se pudo simular la aprobación");
    } finally {
      setSimulating(false);
    }
  }

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-50 bg-neutral-950/80 backdrop-blur-sm flex items-center justify-center p-4 animate-in fade-in duration-200">
      <div className="bg-neutral-900 border border-neutral-800 rounded-3xl p-6 max-w-sm w-full shadow-2xl relative flex flex-col items-center text-center">
        <button
          onClick={onClose}
          className="absolute top-4 right-4 w-8 h-8 rounded-full bg-neutral-800 flex items-center justify-center text-neutral-400 hover:text-neutral-100"
        >
          <XIcon className="w-4 h-4" />
        </button>

        {loading ? (
          <div className="py-12 flex flex-col items-center gap-3">
            <Loader2Icon className="w-10 h-10 animate-spin text-[#009ee3]" />
            <p className="text-sm text-neutral-300">Generando QR de Mercado Pago...</p>
          </div>
        ) : paid ? (
          <div className="py-8 flex flex-col items-center gap-3">
            <CheckCircle2Icon className="w-16 h-16 text-emerald-400 animate-bounce" />
            <h3 className="text-xl font-bold text-neutral-100">¡Pago Confirmado!</h3>
            <p className="text-xs text-neutral-400">
              La orden ha sido pagada y la cuenta ha quedado cerrada.
            </p>
          </div>
        ) : (
          preference && (
            <>
              <div className="flex items-center gap-2 mb-2">
                <span className="w-3 h-3 rounded-full bg-[#009ee3] animate-pulse" />
                <h3 className="text-base font-bold text-neutral-100">
                  Cobro con Mercado Pago
                </h3>
              </div>

              <p className="text-xs text-neutral-400 mb-4">
                Escanea este código QR con la app de Mercado Pago o tu cámara
              </p>

              {qrDataUrl && (
                <div className="p-3 bg-white rounded-2xl shadow-inner mb-4">
                  {/* eslint-disable-next-line @next/next/no-img-element */}
                  <img
                    src={qrDataUrl}
                    alt="Mercado Pago QR"
                    className="w-48 h-48 block mx-auto"
                  />
                </div>
              )}

              <div className="w-full bg-neutral-950/60 border border-neutral-800 rounded-xl p-3 mb-4">
                <span className="text-xs text-neutral-400 block mb-1">Total a cobrar</span>
                <Money amount={preference.total} className="text-2xl font-black text-[#009ee3]" />
                <span className="text-[11px] text-neutral-500 block mt-0.5">
                  Comanda {preference.folio}
                </span>
              </div>

              <div className="flex flex-col gap-2 w-full">
                <a
                  href={preference.initPoint}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="w-full"
                >
                  <Button
                    variant="outline"
                    className="w-full border-[#009ee3]/40 text-[#009ee3] hover:bg-[#009ee3]/10 h-10 text-xs font-semibold gap-1.5"
                  >
                    <ExternalLinkIcon className="w-3.5 h-3.5" />
                    Abrir Checkout en nueva pestaña
                  </Button>
                </a>

                {preference.isMock ? (
                  <Button
                    onClick={handleSimulate}
                    disabled={simulating}
                    className="w-full bg-amber-500 hover:bg-amber-400 text-neutral-950 font-bold h-10 text-xs gap-1.5 shadow-md shadow-amber-500/20"
                  >
                    <SparklesIcon className="w-3.5 h-3.5" />
                    {simulating ? "Simulando aprobación..." : "Simular Aprobación (Modo Demo)"}
                  </Button>
                ) : (
                  <Button
                    onClick={handleManualCheck}
                    disabled={checking || paid}
                    variant="outline"
                    className="w-full border-neutral-700 text-neutral-200 hover:bg-neutral-800 h-10 text-xs font-semibold gap-1.5"
                  >
                    <RefreshCwIcon className={`w-3.5 h-3.5 ${checking ? "animate-spin" : ""}`} />
                    {checking ? "Verificando en Mercado Pago..." : "Ya pagué / Verificar ahora"}
                  </Button>
                )}
              </div>

              <div className="flex items-center gap-1.5 mt-4 text-[11px] text-neutral-400">
                <Loader2Icon className="w-3 h-3 animate-spin text-[#009ee3]" />
                <span>Esperando confirmación del pago en vivo...</span>
              </div>
            </>
          )
        )}
      </div>
    </div>
  );
}
