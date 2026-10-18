import Image from "next/image";
import Link from "next/link";

import { cn } from "@/lib/utils";

/*
 * Logotipo de RestoHub: el emblema original convertido a PNG y recortado.
 * Hay dos tamaños en public/brand; se elige el más chico que se vea nítido en
 * pantallas 2x para no cargar la versión completa (562 KB) en el navbar.
 */
const SIZES = {
  sm: { src: "/brand/restohub-logo-160.png", width: 160, height: 124 },
  lg: { src: "/brand/restohub-logo-320.png", width: 320, height: 249 },
} as const;

export function LogoMark({ size = "sm", className }: { size?: keyof typeof SIZES; className?: string }) {
  const image = SIZES[size];
  return (
    <Image
      src={image.src}
      width={image.width}
      height={image.height}
      alt=""
      aria-hidden="true"
      // Ya vienen al tamaño de uso; se sirven tal cual desde public/.
      unoptimized
      className={cn("h-10 w-auto shrink-0 rounded-md", className)}
    />
  );
}

export function Logo({ size = "sm", className }: { size?: keyof typeof SIZES; className?: string }) {
  return (
    <Link href="/" className={cn("flex items-center gap-2.5", className)} aria-label="RestoHub, inicio">
      <LogoMark size={size} className={size === "lg" ? "h-20" : undefined} />
      <span className="text-[17px] font-semibold tracking-[-0.02em] text-mk-ink">RestoHub</span>
    </Link>
  );
}
