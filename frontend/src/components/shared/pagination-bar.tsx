import { ChevronLeftIcon, ChevronRightIcon } from "lucide-react";

import { Button } from "@/components/ui/button";

interface PaginationBarProps {
  /** Pagina actual, empezando en 0 como en el backend. */
  page: number;
  totalPages: number;
  totalElements: number;
  /** Nombre de lo que se pagina, para el lector de pantalla: "ventas". */
  itemLabel: string;
  onPageChange: (page: number) => void;
  /** Mientras llega la pagina pedida, los botones se bloquean para no saltar dos. */
  isFetching?: boolean;
}

/**
 * Paginador explicito (CAL-16): anterior / siguiente y la posicion actual.
 * Va en un `nav` con nombre propio y anuncia el cambio de pagina con
 * `aria-live`, para que quien usa lector de pantalla sepa que la tabla cambio.
 */
export function PaginationBar({
  page,
  totalPages,
  totalElements,
  itemLabel,
  onPageChange,
  isFetching = false,
}: PaginationBarProps) {
  const pageCount = Math.max(totalPages, 1);
  const isFirst = page <= 0;
  const isLast = page + 1 >= pageCount;

  return (
    <nav
      aria-label={`Paginación de ${itemLabel}`}
      className="mt-4 flex flex-wrap items-center justify-between gap-3"
    >
      <p className="text-sm text-muted-foreground" aria-live="polite">
        Página {page + 1} de {pageCount} · {totalElements} {itemLabel}
      </p>
      <div className="flex gap-2">
        <Button
          type="button"
          variant="outline"
          size="sm"
          onClick={() => onPageChange(page - 1)}
          disabled={isFirst || isFetching}
        >
          <ChevronLeftIcon aria-hidden />
          Anterior
        </Button>
        <Button
          type="button"
          variant="outline"
          size="sm"
          onClick={() => onPageChange(page + 1)}
          disabled={isLast || isFetching}
        >
          Siguiente
          <ChevronRightIcon aria-hidden />
        </Button>
      </div>
    </nav>
  );
}
