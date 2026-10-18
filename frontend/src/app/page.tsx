import type { Metadata } from "next";
import Link from "next/link";
import { ArrowRightIcon } from "lucide-react";

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
  title: "Callejón 9 · Operación de restaurantes",
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
              <p className="inline-flex items-center gap-2 rounded-full border border-mk-border bg-mk-surface px-3 py-1 text-[13px] text-mk-muted">
                <span className="size-1.5 rounded-full bg-mk-orange" aria-hidden="true" />
                Mesas, cocina, caja e inventario
              </p>
              <h1
                id="hero-titulo"
                className="mt-6 text-[40px] leading-[1.04] font-semibold tracking-[-0.035em] text-balance text-mk-ink sm:text-[58px]"
              >
                Tu restaurante. Toda la operación. En un solo lugar.
              </h1>
              <p className="mx-auto mt-6 max-w-[640px] text-[18px] leading-relaxed text-pretty text-mk-muted">
                Administra mesas, pedidos, cocina, inventario y ventas en tiempo real con una plataforma diseñada
                para simplificar la operación de tu restaurante.
              </p>
              <div className="mt-9 flex flex-col justify-center gap-3 sm:flex-row">
                <Link
                  href="/signup"
                  className="inline-flex items-center justify-center gap-2 rounded-md bg-mk-orange px-5 py-3 text-[15px] font-medium text-mk-ink transition-colors hover:bg-mk-orange-2"
                >
                  Crear mi restaurante
                  <ArrowRightIcon className="size-4" aria-hidden="true" />
                </Link>
                <a
                  href="#flujo"
                  className="inline-flex items-center justify-center rounded-md border border-mk-border-strong bg-mk-surface px-5 py-3 text-[15px] font-medium text-mk-ink transition-colors hover:bg-mk-bg"
                >
                  Ver cómo funciona
                </a>
              </div>
              <p className="mt-4 text-[13px] text-mk-muted">Plan Gratis disponible: 3 usuarios y 5 mesas, sin costo.</p>
            </div>

            <div className="relative isolate mt-14 sm:mt-16">
              <div
                className="absolute inset-x-10 -top-6 bottom-10 -z-10 rounded-[28px] bg-mk-orange-tint"
                aria-hidden="true"
              />
              <ProductPreview />
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
