import type { ReactNode } from "react";

import { Skeleton } from "@/components/ui/skeleton";
import { ApiError } from "@/lib/api";

interface QueryStateProps {
  isLoading: boolean;
  error?: unknown;
  isEmpty?: boolean;
  emptyMessage?: string;
  /** Esqueleto con la forma final del bloque. Nunca un spinner centrado. */
  skeleton?: ReactNode;
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
  skeleton,
  children,
}: QueryStateProps) {
  if (isLoading) {
    return (
      skeleton ?? (
        <div className="flex flex-col gap-2">
          <Skeleton className="h-12 w-full" />
          <Skeleton className="h-12 w-full" />
          <Skeleton className="h-12 w-full" />
        </div>
      )
    );
  }

  if (error) {
    const message =
      error instanceof ApiError && error.status === 403
        ? "No tienes permiso para ver esta información."
        : error instanceof Error
          ? error.message
          : "Ocurrió un error al cargar la información.";

    return (
      <div className="rounded-md border border-destructive/50 px-3.5 py-3">
        <p className="eyebrow text-destructive/80">Error</p>
        <p className="mt-0.5 text-sm text-destructive">{message}</p>
      </div>
    );
  }

  if (isEmpty) {
    return <EmptyState message={emptyMessage} />;
  }

  return <>{children}</>;
}

/** Estado vacio del sistema: eyebrow y una frase. Sin ilustracion. */
export function EmptyState({ message, label = "Sin datos" }: { message: string; label?: string }) {
  return (
    <div className="py-6">
      <p className="eyebrow">{label}</p>
      <p className="mt-1 text-sm text-muted-foreground">{message}</p>
    </div>
  );
}
