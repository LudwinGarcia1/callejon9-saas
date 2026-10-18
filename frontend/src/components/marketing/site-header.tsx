import Link from "next/link";
import { MenuIcon } from "lucide-react";

import { Logo } from "./logo";

export const NAV_LINKS = [
  { href: "#funcionalidades", label: "Funcionalidades" },
  { href: "#beneficios", label: "Beneficios" },
  { href: "#precios", label: "Precios" },
  { href: "#preguntas", label: "Preguntas frecuentes" },
] as const;

/**
 * Barra superior del sitio público. El menú móvil es un <details>: abre y
 * cierra sin JavaScript y conserva la semántica de botón para el teclado.
 */
export function SiteHeader() {
  return (
    <header className="sticky top-0 z-40 border-b border-mk-border bg-mk-bg/90 backdrop-blur-sm">
      <div className="mx-auto flex h-16 max-w-[1160px] items-center justify-between gap-6 px-4 sm:px-6">
        <Logo />

        <nav aria-label="Principal" className="hidden items-center gap-7 lg:flex">
          {NAV_LINKS.map((link) => (
            <a
              key={link.href}
              href={link.href}
              className="text-sm text-mk-muted transition-colors hover:text-mk-ink"
            >
              {link.label}
            </a>
          ))}
        </nav>

        <div className="hidden items-center gap-2 lg:flex">
          <Link
            href="/login"
            className="rounded-md px-3 py-2 text-sm font-medium text-mk-ink transition-colors hover:bg-mk-border/60"
          >
            Iniciar sesión
          </Link>
          <Link
            href="/signup"
            className="rounded-md bg-mk-ink px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-mk-ink/85"
          >
            Crear mi restaurante
          </Link>
        </div>

        <details className="group relative lg:hidden">
          <summary
            aria-label="Abrir menú"
            className="flex size-10 cursor-pointer list-none items-center justify-center rounded-md border border-mk-border text-mk-ink [&::-webkit-details-marker]:hidden"
          >
            <MenuIcon className="size-5" aria-hidden="true" />
          </summary>
          <div className="absolute right-0 mt-2 w-64 rounded-lg border border-mk-border bg-mk-surface p-2 shadow-[0_12px_32px_-12px_rgba(13,13,13,0.25)]">
            <nav aria-label="Principal (móvil)" className="flex flex-col">
              {NAV_LINKS.map((link) => (
                <a
                  key={link.href}
                  href={link.href}
                  className="rounded-md px-3 py-2.5 text-sm text-mk-text hover:bg-mk-bg"
                >
                  {link.label}
                </a>
              ))}
            </nav>
            <div className="mt-2 flex flex-col gap-2 border-t border-mk-border pt-2">
              <Link href="/login" className="rounded-md px-3 py-2.5 text-sm font-medium text-mk-ink hover:bg-mk-bg">
                Iniciar sesión
              </Link>
              <Link
                href="/signup"
                className="rounded-md bg-mk-ink px-3 py-2.5 text-center text-sm font-medium text-white"
              >
                Crear mi restaurante
              </Link>
            </div>
          </div>
        </details>
      </div>
    </header>
  );
}
