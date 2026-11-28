"use client";

import { Suspense, useState } from "react";
import { useSearchParams } from "next/navigation";
import { CheckCircle2Icon, CreditCardIcon, Loader2Icon, ShieldCheckIcon, WalletIcon, XCircleIcon } from "lucide-react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { api } from "@/lib/api";
import { endpoints } from "@/lib/endpoints";

function MockCheckoutContent() {
  const searchParams = useSearchParams();
  const prefId = searchParams.get("pref_id") || "MOCK-PREF-UNKNOWN";
  const ref = searchParams.get("ref") || "";
  const total = searchParams.get("total") || "0.00";
  const folio = searchParams.get("folio") || "ORD-DEMO";

  const [loading, setLoading] = useState(false);
  const [status, setStatus] = useState<"pending" | "approved" | "rejected">("pending");
  const [selectedMethod, setSelectedMethod] = useState<"card" | "wallet">("card");

  async function handlePay() {
    setLoading(true);
    try {
      const mockPaymentId = "MOCK-PAY-" + Date.now();
      await api.post(endpoints.webhooks.mercadopago(), {
        action: "payment.created",
        external_reference: ref,
        data: { id: mockPaymentId },
      });

      setStatus("approved");
      toast.success("¡Pago aprobado por Mercado Pago Sandbox!");
    } catch (err) {
      toast.error("Error al procesar el pago simulado");
      console.error(err);
    } finally {
      setLoading(false);
    }
  }

  function handleCancel() {
    setStatus("rejected");
    toast.error("Pago rechazado");
  }

  return (
    <div className="min-h-screen bg-neutral-950 text-neutral-100 flex flex-col items-center justify-center p-4">
      <div className="max-w-md w-full bg-neutral-900 border border-neutral-800 rounded-3xl shadow-2xl overflow-hidden">
        {/* Header institucional Mercado Pago */}
        <div className="bg-[#009ee3] px-6 py-5 text-white flex items-center justify-between">
          <div className="flex items-center gap-2">
            <div className="w-8 h-8 rounded-full bg-white text-[#009ee3] font-black flex items-center justify-center text-sm shadow">
              MP
            </div>
            <div>
              <h1 className="text-base font-black tracking-tight leading-none">Mercado Pago</h1>
              <span className="text-[11px] font-medium opacity-90">Ambiente de Pruebas (Sandbox)</span>
            </div>
          </div>
          <span className="text-[10px] uppercase font-bold tracking-wider px-2 py-0.5 rounded-full bg-white/20">
            Demo
          </span>
        </div>

        <div className="p-6">
          {status === "approved" ? (
            <div className="py-8 flex flex-col items-center text-center gap-4 animate-in fade-in duration-300">
              <div className="w-16 h-16 rounded-full bg-emerald-500/20 text-emerald-400 flex items-center justify-center">
                <CheckCircle2Icon className="w-10 h-10" />
              </div>
              <h2 className="text-2xl font-black text-neutral-100">¡Pago Aprobado!</h2>
              <p className="text-sm text-neutral-400">
                Tu cobro para la comanda <strong className="text-neutral-200">{folio}</strong> ha sido confirmado en el sistema.
              </p>
              <div className="bg-neutral-950/60 border border-neutral-800 rounded-xl p-4 w-full text-xs text-neutral-400 space-y-1">
                <div className="flex justify-between">
                  <span>Monto abonado:</span>
                  <span className="text-neutral-200 font-bold">${total} MXN</span>
                </div>
                <div className="flex justify-between">
                  <span>Referencia:</span>
                  <span className="font-mono text-[11px] text-neutral-500 truncate max-w-[200px]">{ref || prefId}</span>
                </div>
              </div>

              <Button
                onClick={() => window.history.back()}
                className="w-full bg-[#009ee3] hover:bg-[#009ee3]/90 text-white font-bold h-11 rounded-xl"
              >
                Volver a la comanda
              </Button>
            </div>
          ) : status === "rejected" ? (
            <div className="py-8 flex flex-col items-center text-center gap-4 animate-in fade-in duration-300">
              <div className="w-16 h-16 rounded-full bg-rose-500/20 text-rose-400 flex items-center justify-center">
                <XCircleIcon className="w-10 h-10" />
              </div>
              <h2 className="text-2xl font-black text-neutral-100">Pago Cancelado</h2>
              <p className="text-sm text-neutral-400">
                Has cancelado el proceso de pago. La comanda {folio} sigue pendiente.
              </p>
              <Button
                variant="outline"
                onClick={() => setStatus("pending")}
                className="w-full border-neutral-700 text-neutral-200"
              >
                Reintentar
              </Button>
            </div>
          ) : (
            <>
              {/* Resumen de orden */}
              <div className="mb-6 pb-6 border-b border-neutral-800 flex justify-between items-end">
                <div>
                  <span className="text-xs text-neutral-400 uppercase tracking-wider font-semibold block mb-0.5">
                    Comanda en RestoHub
                  </span>
                  <h2 className="text-xl font-bold text-neutral-100">{folio}</h2>
                  <span className="text-[11px] text-neutral-500 font-mono">Pref: {prefId.slice(0, 18)}…</span>
                </div>
                <div className="text-right">
                  <span className="text-xs text-neutral-400 block mb-0.5">Total a pagar</span>
                  <span className="text-2xl font-black text-[#009ee3]">${total}</span>
                </div>
              </div>

              {/* Selección de método demo */}
              <div className="mb-6 space-y-2.5">
                <label className="text-xs text-neutral-400 font-medium block">
                  Selecciona método de pago simulado:
                </label>

                <button
                  type="button"
                  onClick={() => setSelectedMethod("card")}
                  className={`w-full flex items-center gap-3 p-3.5 rounded-2xl border text-left transition-all ${
                    selectedMethod === "card"
                      ? "border-[#009ee3] bg-[#009ee3]/10 text-neutral-100 ring-1 ring-[#009ee3]"
                      : "border-neutral-800 bg-neutral-950/40 text-neutral-400 hover:border-neutral-700"
                  }`}
                >
                  <CreditCardIcon className="w-5 h-5 text-[#009ee3]" />
                  <div className="flex-1">
                    <p className="text-xs font-bold text-neutral-200">Tarjeta Visa Demo</p>
                    <p className="text-[11px] text-neutral-400">•••• •••• •••• 4242 · Vence 12/28</p>
                  </div>
                  <span className="w-3.5 h-3.5 rounded-full border border-neutral-600 flex items-center justify-center">
                    {selectedMethod === "card" && <span className="w-2 h-2 rounded-full bg-[#009ee3]" />}
                  </span>
                </button>

                <button
                  type="button"
                  onClick={() => setSelectedMethod("wallet")}
                  className={`w-full flex items-center gap-3 p-3.5 rounded-2xl border text-left transition-all ${
                    selectedMethod === "wallet"
                      ? "border-[#009ee3] bg-[#009ee3]/10 text-neutral-100 ring-1 ring-[#009ee3]"
                      : "border-neutral-800 bg-neutral-950/40 text-neutral-400 hover:border-neutral-700"
                  }`}
                >
                  <WalletIcon className="w-5 h-5 text-emerald-400" />
                  <div className="flex-1">
                    <p className="text-xs font-bold text-neutral-200">Dinero en cuenta Mercado Pago</p>
                    <p className="text-[11px] text-neutral-400">Saldo ficticio disponible: $10,000.00</p>
                  </div>
                  <span className="w-3.5 h-3.5 rounded-full border border-neutral-600 flex items-center justify-center">
                    {selectedMethod === "wallet" && <span className="w-2 h-2 rounded-full bg-emerald-400" />}
                  </span>
                </button>
              </div>

              {/* Botones de acción */}
              <div className="flex flex-col gap-2.5">
                <Button
                  onClick={handlePay}
                  disabled={loading}
                  className="w-full bg-[#009ee3] hover:bg-[#009ee3]/90 text-white font-extrabold h-12 rounded-xl text-sm shadow-lg shadow-[#009ee3]/20 gap-2"
                >
                  {loading ? (
                    <>
                      <Loader2Icon className="w-4 h-4 animate-spin" />
                      Procesando pago con Mercado Pago…
                    </>
                  ) : (
                    <>Pagar ${total} MXN</>
                  )}
                </Button>

                <Button
                  variant="ghost"
                  onClick={handleCancel}
                  disabled={loading}
                  className="w-full text-neutral-400 hover:text-neutral-200 h-10 text-xs"
                >
                  Cancelar y regresar
                </Button>
              </div>

              <div className="mt-6 flex items-center justify-center gap-1.5 text-[11px] text-neutral-500">
                <ShieldCheckIcon className="w-3.5 h-3.5 text-emerald-500" />
                <span>Simulador oficial integrado para pruebas locales y presentaciones</span>
              </div>
            </>
          )}
        </div>
      </div>
    </div>
  );
}

export default function MercadoPagoMockPage() {
  return (
    <Suspense
      fallback={
        <div className="min-h-screen bg-neutral-950 flex items-center justify-center text-neutral-400 text-sm">
          Cargando checkout de Mercado Pago...
        </div>
      }
    >
      <MockCheckoutContent />
    </Suspense>
  );
}
