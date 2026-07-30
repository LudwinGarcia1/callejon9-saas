import type { ReactNode } from "react";

import { Skeleton } from "@/components/ui/skeleton";
import { ApiError } from "@/lib/api";

interface QueryStateProps {
  isLoading: boolean;
  error?: unknown;
  isEmpty?: boolean;
  emptyMessage?: string;
  children: ReactNode;
}

/**
 * Envoltorio comun para los tres estados de una consulta: cargando, con
 * error o vacia. Un 401 nunca llega a renderizarse aqui — lo intercepta el
 * QueryClient en providers.tsx antes, redirigiendo a login. Un 403 si se
 * muestra localmente como "sin permiso", porque significa rol equivocado o
 * falta de contexto de tenant, no sesion perdida.
 */
export function QueryState({
  isLoading,
  error,
  isEmpty = false,
  emptyMessage = "No hay datos para mostrar.",
  children,
}: QueryStateProps) {
  if (isLoading) {
    return (
      <div className="flex flex-col gap-2">
        <Skeleton className="h-10 w-full" />
        <Skeleton className="h-10 w-full" />
        <Skeleton className="h-10 w-full" />
      </div>
    );
  }

  if (error) {
    const message =
      error instanceof ApiError && error.status === 403
        ? "No tienes permiso para ver esta informacion."
        : error instanceof Error
          ? error.message
          : "Ocurrio un error al cargar la informacion.";

    return <p className="text-sm text-destructive">{message}</p>;
  }

  if (isEmpty) {
    return <p className="text-sm text-muted-foreground">{emptyMessage}</p>;
  }

  return <>{children}</>;
}
