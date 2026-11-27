"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import type { ReactNode } from "react";

import { canOpenPage, homeSectionFor } from "@/components/layout/nav";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { useSession } from "@/hooks/use-session";
import { ApiError } from "@/lib/api";

export const NO_PERMISSION_MESSAGE = "No tienes permiso para ver esta información.";

/**
 * Pinta la pantalla solo si el rol de la sesion la tiene en su navegacion
 * (CAL-195). Antes, quien escribia en la barra de direcciones la ruta de otro
 * rol veia esa pantalla con lo que su rol si podia consultar --mesas,
 * productos, la cola de cobro-- y sin ningun aviso.
 *
 * Mientras no se conoce el rol no se monta la pantalla: asi ninguna de sus
 * consultas sale antes de saber si le corresponde. La autorizacion real sigue
 * en el backend; esto solo decide que se muestra.
 */
export function PageAccess({ children }: { children: ReactNode }) {
  const { user, isLoading, error } = useSession();
  const pathname = usePathname();

  if (isLoading) {
    return <PageAccessSkeleton />;
  }

  if (!user) {
    // Un 401 ya va camino al inicio de sesion (providers.tsx); repetirlo aqui
    // solo dejaria un error a la vista durante la redireccion.
    if (error instanceof ApiError && error.status === 401) {
      return <PageAccessSkeleton />;
    }
    return (
      <div className="px-[18px] py-3.5 sm:px-5 sm:py-[18px] lg:px-8 lg:py-[26px]">
        <div className="rounded-md border border-destructive/50 px-3.5 py-3">
          <p className="eyebrow text-destructive/80">Error</p>
          <p className="mt-0.5 text-sm text-destructive">No se pudo cargar tu sesión. Intenta de nuevo.</p>
        </div>
      </div>
    );
  }

  if (!canOpenPage(user.role, pathname)) {
    const home = homeSectionFor(user.role);
    return (
      <section
        aria-labelledby="page-access-title"
        className="px-[18px] py-3.5 sm:px-5 sm:py-[18px] lg:px-8 lg:py-[26px]"
      >
        <p className="eyebrow text-destructive/80">Sin permiso</p>
        <h1 id="page-access-title" className="mt-1 text-sm font-medium">
          {NO_PERMISSION_MESSAGE}
        </h1>
        <p className="mt-1 text-sm text-muted-foreground">
          Esta pantalla no corresponde a tu rol.
        </p>
        <Button asChild variant="outline" className="mt-4">
          <Link href={home.href}>Ir a {home.label}</Link>
        </Button>
      </section>
    );
  }

  return <>{children}</>;
}

function PageAccessSkeleton() {
  return (
    <div
      aria-busy="true"
      className="flex flex-col gap-2 px-[18px] py-3.5 sm:px-5 sm:py-[18px] lg:px-8 lg:py-[26px]"
    >
      <Skeleton className="h-9 w-48" />
      <Skeleton className="h-12 w-full" />
      <Skeleton className="h-12 w-full" />
    </div>
  );
}
