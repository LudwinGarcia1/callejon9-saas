import Image from "next/image";
import Link from "next/link";

import { cn } from "@/lib/utils";

/**
 * Marca de la plataforma (RestoHub) para las pantallas sin restaurante:
 * entrada, registro, 404 y error. Enlaza al sitio público. El emblema es el
 * mismo PNG de la landing; el color del nombre lo hereda del contenedor para
 * leerse igual sobre el panel en tinta del login que sobre fondo claro.
 */
export function PlatformBrand({ className }: { className?: string }) {
  return (
    <Link href="/" aria-label="RestoHub, ir al inicio" className={cn("inline-flex items-center gap-3", className)}>
      <Image
        src="/brand/restohub-logo-160.png"
        width={160}
        height={123}
        alt=""
        aria-hidden="true"
        // Ya viene al tamaño de uso; se sirve tal cual desde public/.
        unoptimized
        className="h-11 w-auto shrink-0"
      />
      <span className="font-display text-[26px] leading-none">RestoHub</span>
    </Link>
  );
}
