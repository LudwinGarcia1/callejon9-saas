import type { Metadata } from "next";
import Link from "next/link";
import { ArrowRightIcon } from "lucide-react";

import { delay } from "@/components/marketing/motion";

import { ProductPreview } from "@/components/marketing/product-preview";
import {
  BenefitsSection,
  Container,
  FaqSection,
  FeaturesSection,
  FinalCtaSection,
  InventorySalesSection,
  OrderFlowSection,
  PricingSection,
  ProblemsSection,
  ReportsSection,
} from "@/components/marketing/sections";
import { SiteFooter } from "@/components/marketing/site-footer";
import { SiteHeader } from "@/components/marketing/site-header";

export const metadata: Metadata = {
  title: "RestoHub · Operación de restaurantes",
  description:
    "Administra mesas, pedidos, cocina, inventario y ventas en tiempo real con una plataforma diseñada para simplificar la operación de tu restaurante.",
};

/**
 * Sitio público. Antes la raíz redirigía a /login; ahora presenta el
 * producto y deja el acceso a un clic. No consulta la sesión: hacerlo con
 * GET /auth/me dispararía el aviso de sesión expirada a visitantes anónimos.
 */
export default function Home() {
  return (
    <div className="marketing min-h-screen">
      <a
        href="#contenido"
        className="sr-only focus:not-sr-only focus:absolute focus:top-3 focus:left-3 focus:z-50 focus:rounded-md focus:bg-mk-ink focus:px-4 focus:py-2 focus:text-white"
      >
        Saltar al contenido
      </a>
      <SiteHeader />

      <main id="contenido">
        <section aria-labelledby="hero-titulo" className="overflow-hidden pt-16 pb-20 sm:pt-24">
          <Container>
            <div className="mx-auto max-w-[820px] text-center">
              <p style={delay(0)} className="mk-enter inline-flex items-center gap-2 rounded-full border border-mk-border bg-mk-surface px-3 py-1 text-[13px] text-mk-muted">
                <span className="size-1.5 rounded-full bg-mk-orange" aria-hidden="true" />
                Mesas, cocina, caja e inventario
              </p>
              <h1
                id="hero-titulo"
                style={delay(80)}
                className="mk-enter mt-6 text-[40px] leading-[1.04] font-semibold tracking-[-0.035em] text-balance text-mk-ink sm:text-[58px]"
              >
                Tu restaurante. Toda la operación. En un solo lugar.
              </h1>
              <p style={delay(160)} className="mk-enter mx-auto mt-6 max-w-[640px] text-[18px] leading-relaxed text-pretty text-mk-muted">
                Administra mesas, pedidos, cocina, inventario y ventas en tiempo real con una plataforma diseñada
                para simplificar la operación de tu restaurante.
              </p>
              <div style={delay(240)} className="mk-enter mt-9 flex flex-col justify-center gap-3 sm:flex-row">
                <Link
                  href="/signup"
                  className="group inline-flex items-center justify-center gap-2 rounded-md bg-mk-orange px-5 py-3 text-[15px] font-medium text-mk-ink transition-colors hover:bg-mk-orange-2"
                >
                  Crear mi restaurante
                  <ArrowRightIcon className="size-4 transition-transform group-hover:translate-x-0.5" aria-hidden="true" />
                </Link>
                <a
                  href="#flujo"
                  className="inline-flex items-center justify-center rounded-md border border-mk-border-strong bg-mk-surface px-5 py-3 text-[15px] font-medium text-mk-ink transition-colors hover:bg-mk-bg"
                >
                  Ver cómo funciona
                </a>
              </div>
              <p style={delay(300)} className="mk-enter mt-4 text-[13px] text-mk-muted">Plan Gratis disponible: 3 usuarios y 5 mesas, sin costo.</p>
            </div>

            <div style={delay(420)} className="mk-enter relative isolate mt-14 sm:mt-16">
              <div
                className="absolute inset-x-10 -top-6 bottom-10 -z-10 rounded-[28px] bg-mk-orange-tint"
                aria-hidden="true"
              />
              <ProductPreview />
              {/* Aviso de cocina: muestra que lo que pasa en una pantalla llega a las demás. */}
              <div
                aria-hidden="true"
                style={delay(1700)}
                className="mk-toast absolute -bottom-6 left-6 hidden w-[280px] rounded-lg border border-mk-border-strong bg-mk-surface p-3.5 shadow-[0_20px_40px_-20px_rgba(13,13,13,0.35)] md:block lg:left-[200px]"
              >
                <div className="flex items-center justify-between">
                  <p className="font-mono text-[10px] tracking-[0.12em] text-mk-muted uppercase">Cocina · Mesa 4</p>
                  <span className="text-[11px] text-mk-muted">ahora</span>
                </div>
                <p className="mt-1.5 flex items-center gap-2 text-[13px] text-mk-ink">
                  <span className="size-1.5 rounded-full bg-emerald-600" />3 × Agua de horchata lista para servir
                </p>
              </div>
            </div>
          </Container>
        </section>

        <ProblemsSection />
        <FeaturesSection />
        <OrderFlowSection />
        <InventorySalesSection />
        <ReportsSection />
        <BenefitsSection />
        <PricingSection />
        <FaqSection />
        <FinalCtaSection />
      </main>

      <SiteFooter />
    </div>
  );
}
