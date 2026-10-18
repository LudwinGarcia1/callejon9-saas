import Link from "next/link";

import { Logo } from "./logo";

const COLUMNS = [
  {
    title: "Producto",
    links: [
      { href: "#funcionalidades", label: "Funcionalidades" },
      { href: "#flujo", label: "Flujo de pedidos" },
      { href: "#beneficios", label: "Beneficios" },
      { href: "#precios", label: "Precios" },
    ],
  },
  {
    title: "Cuenta",
    links: [
      { href: "/signup", label: "Crear mi restaurante" },
      { href: "/login", label: "Iniciar sesión" },
    ],
  },
  {
    title: "Ayuda",
    links: [{ href: "#preguntas", label: "Preguntas frecuentes" }],
  },
] as const;

export function SiteFooter() {
  return (
    <footer className="border-t border-mk-border bg-mk-surface">
      <div className="mx-auto grid max-w-[1160px] grid-cols-2 gap-x-6 gap-y-10 px-4 py-12 sm:px-6 sm:py-14 md:grid-cols-[1.4fr_repeat(3,1fr)]">
        <div className="col-span-2 md:col-span-1">
          <Logo size="lg" />
          <p className="mt-4 max-w-[300px] text-[14px] leading-relaxed text-mk-muted">
            Plataforma para operar restaurantes: mesas, pedidos, cocina, caja, inventario y reportes.
          </p>
        </div>
        {COLUMNS.map((column) => (
          <nav key={column.title} aria-label={column.title}>
            <p className="text-[13px] font-semibold text-mk-ink">{column.title}</p>
            <ul className="mt-4 space-y-2.5">
              {column.links.map((link) => (
                <li key={link.href}>
                  {link.href.startsWith("#") ? (
                    <a href={link.href} className="text-[14px] text-mk-muted transition-colors hover:text-mk-ink">
                      {link.label}
                    </a>
                  ) : (
                    <Link href={link.href} className="text-[14px] text-mk-muted transition-colors hover:text-mk-ink">
                      {link.label}
                    </Link>
                  )}
                </li>
              ))}
            </ul>
          </nav>
        ))}
      </div>
      <div className="border-t border-mk-border">
        <p className="mx-auto max-w-[1160px] px-4 py-6 text-[13px] text-mk-muted sm:px-6">
          © {new Date().getFullYear()} RestoHub. Precios en pesos mexicanos.
        </p>
      </div>
    </footer>
  );
}
