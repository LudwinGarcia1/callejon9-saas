"use client";

import { BellRing } from "lucide-react";

import { Button } from "@/components/ui/button";
import { useWaiterCalls } from "@/hooks/use-waiter-calls";
import { formatShortTime } from "@/lib/format";

/**
 * Franja con las mesas que llamaron al mesero y nadie ha atendido. Va arriba
 * de cualquier pantalla del mesero, para que la llamada no se pierda si el
 * aviso emergente se cerro o el mesero estaba dentro de una comanda.
 */
export function WaiterCallAlerts() {
  const { calls, dismiss } = useWaiterCalls();

  return (
    <section
      aria-label="Mesas que llaman al mesero"
      aria-live="polite"
      data-tone="amber"
      className={calls.length === 0 ? "sr-only" : "border-b border-[var(--tone-border)] bg-[var(--tone-bg)] px-[18px] py-3 sm:px-5 lg:px-8"}
    >
      {calls.length > 0 && (
        <ul className="flex flex-wrap gap-2">
          {calls.map((call) => (
            <li
              key={call.tableId}
              className="flex items-center gap-3 rounded-md border border-[var(--tone-border)] bg-card py-1.5 pr-1.5 pl-3"
            >
              <BellRing aria-hidden className="size-4 text-[var(--tone)]" />
              <span className="text-sm">
                <span className="font-medium">Mesa {call.tableNumber}</span>
                <span className="text-muted-foreground"> · llamó a las {formatShortTime(call.calledAt)}</span>
              </span>
              <Button
                size="sm"
                variant="outline"
                className="h-11 lg:h-8"
                aria-label={`Marcar como atendida la mesa ${call.tableNumber}`}
                onClick={() => dismiss(call.tableId)}
              >
                Atendida
              </Button>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
