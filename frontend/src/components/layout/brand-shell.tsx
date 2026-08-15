import type { ReactNode } from "react";

/**
 * Marco de las pantallas sin sesion (entrada, 404, error): panel de marca de
 * la plataforma sobre superficie, centrado y con el mismo ritmo tipografico
 * que login. No lleva identidad de restaurante: fuera de la sesion no hay
 * restaurante todavia.
 */
export function BrandShell({
  eyebrow,
  title,
  children,
}: {
  eyebrow: string;
  title: string;
  children?: ReactNode;
}) {
  return (
    <div className="flex min-h-screen flex-col items-center justify-center gap-8 px-6 py-12 text-center">
      <div>
        <p className="font-display text-[26px] leading-none">Callejón 9</p>
        <p className="eyebrow mt-1.5 tracking-[0.14em]">Operación de restaurantes</p>
      </div>

      <div className="max-w-[440px]">
        <p className="eyebrow tracking-[0.14em]">{eyebrow}</p>
        <h1 className="mt-1.5 font-display text-[34px] leading-[1.05] font-normal">{title}</h1>
        {children}
      </div>
    </div>
  );
}
