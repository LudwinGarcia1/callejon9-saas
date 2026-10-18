import Link from "next/link";

import { cn } from "@/lib/utils";

/**
 * Logotipo de la plataforma: monograma C9 sobre naranja y el nombre en
 * tinta. El monograma va en SVG para que no dependa de la fuente cargada.
 */
export function LogoMark({ className }: { className?: string }) {
  return (
    <svg viewBox="0 0 32 32" aria-hidden="true" className={cn("size-8 shrink-0", className)}>
      <rect width="32" height="32" rx="7" fill="#F2913D" />
      <path
        d="M14.2 10.4a6 6 0 1 0 0 11.2"
        fill="none"
        stroke="#0D0D0D"
        strokeWidth="2.6"
        strokeLinecap="round"
      />
      <circle cx="20.6" cy="13.9" r="3.1" fill="none" stroke="#0D0D0D" strokeWidth="2.4" />
      <path d="M23.7 13.9v4.4a3.6 3.6 0 0 1-3.6 3.6" fill="none" stroke="#0D0D0D" strokeWidth="2.4" strokeLinecap="round" />
    </svg>
  );
}

export function Logo({ className }: { className?: string }) {
  return (
    <Link href="/" className={cn("flex items-center gap-2.5", className)} aria-label="Callejón 9, inicio">
      <LogoMark />
      <span className="text-[17px] font-semibold tracking-[-0.02em] text-mk-ink">Callejón 9</span>
    </Link>
  );
}
