"use client";

import { useEffect, useRef, useState } from "react";

import { cn } from "@/lib/utils";

/*
 * Simulación del tablero de cocina: las comandas enviadas, la más antigua
 * primero, y cada platillo avanzando por los mismos cuatro estados que el
 * sistema. Corre solo mientras está en pantalla y nunca con movimiento
 * reducido. Es decorativa: la sección la explica con texto.
 */

type Status = 0 | 1 | 2 | 3;

const STATUS_LABEL = ["Pendiente", "En preparación", "Listo", "Entregado"] as const;

const STATUS_STYLE = [
  "border-mk-night-border text-mk-night-muted",
  "border-mk-orange/50 bg-mk-orange/15 text-mk-orange",
  "border-emerald-500/40 bg-emerald-500/15 text-emerald-300",
  "border-transparent text-mk-night-muted/70",
] as const;

const TICKETS = [
  { table: 2, folio: "ORD-0409", waited: "hace 6 min", items: ["2 × Enchiladas de mole", "1 × Sopa de tortilla"] },
  { table: 4, folio: "ORD-0412", waited: "hace 4 min", items: ["2 × Arrachera 300 g", "3 × Agua de horchata", "2 × Flan napolitano"] },
  { table: 7, folio: "ORD-0415", waited: "hace 1 min", items: ["1 × Queso fundido", "2 × Margarita"] },
];

const INITIAL: Status[][] = [
  [1, 2],
  [1, 0, 0],
  [0, 0],
];

/**
 * Orden en que avanzan los platillos: [comanda, platillo]. Cada platillo
 * aparece tantas veces como pasos le faltan desde INITIAL hasta Entregado.
 */
const SCRIPT: [number, number][] = [
  [0, 0], [1, 1], [0, 1], [2, 0], [1, 0], [0, 0], [1, 1], [2, 1], [1, 2],
  [1, 0], [2, 0], [1, 2], [2, 1], [1, 1], [1, 2], [2, 0], [2, 1],
];

const TICK_MS = 1800;

export function KitchenLive() {
  const [statuses, setStatuses] = useState<Status[][]>(INITIAL);
  const [changed, setChanged] = useState<string | null>(null);
  const step = useRef(0);
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const element = ref.current;
    if (!element) return;
    if (window.matchMedia("(prefers-reduced-motion: reduce)").matches) return;

    let timer: number | undefined;
    const tick = () => {
      if (step.current >= SCRIPT.length) {
        step.current = 0;
        setStatuses(INITIAL);
        setChanged(null);
        return;
      }
      const [ticket, item] = SCRIPT[step.current];
      step.current += 1;
      setStatuses((current) =>
        current.map((row, t) =>
          t === ticket ? row.map((value, i) => (i === item ? (Math.min(3, value + 1) as Status) : value)) : row,
        ),
      );
      setChanged(`${ticket}-${item}`);
    };

    const observer = new IntersectionObserver(([entry]) => {
      window.clearInterval(timer);
      if (entry.isIntersecting) timer = window.setInterval(tick, TICK_MS);
    });
    observer.observe(element);
    return () => {
      observer.disconnect();
      window.clearInterval(timer);
    };
  }, []);

  return (
    <div
      ref={ref}
      aria-hidden="true"
      className="-mx-4 flex snap-x snap-mandatory gap-3 overflow-x-auto px-4 pb-2 [scrollbar-width:none] sm:-mx-6 sm:px-6 md:mx-0 md:grid md:grid-cols-3 md:gap-4 md:overflow-visible md:px-0 md:pb-0"
    >
      {TICKETS.map((ticket, t) => {
        const ready = statuses[t].every((value) => value >= 2);
        return (
          <div
            key={ticket.folio}
            className="w-[84%] shrink-0 snap-start rounded-xl border border-mk-night-border bg-mk-night-raised p-4 sm:w-[60%] md:w-auto"
          >
            <div className="flex items-start justify-between">
              <div>
                <p className="text-[17px] font-semibold text-white">Mesa {ticket.table}</p>
                <p className="font-mono text-[11px] text-mk-night-muted">
                  {ticket.folio} · {ticket.waited}
                </p>
              </div>
              <span
                className={cn(
                  "rounded-full px-2 py-0.5 font-mono text-[10px] tracking-[0.08em] uppercase transition-colors duration-500",
                  ready ? "bg-emerald-500/15 text-emerald-300" : "bg-white/5 text-mk-night-muted",
                )}
              >
                {ready ? "Lista" : "Enviada"}
              </span>
            </div>
            <div className="mt-4 space-y-2">
              {ticket.items.map((name, i) => {
                const status = statuses[t][i];
                return (
                  <div
                    key={name}
                    className={cn(
                      "flex items-center justify-between gap-3 rounded-lg border border-mk-night-border px-3 py-2.5 transition-colors duration-500",
                      changed === `${t}-${i}` ? "bg-white/[0.06]" : "bg-transparent",
                    )}
                  >
                    <span className={cn("text-[13px]", status === 3 ? "text-mk-night-muted line-through" : "text-white")}>
                      {name}
                    </span>
                    <span
                      className={cn(
                        "shrink-0 rounded-full border px-2 py-0.5 text-[11px] transition-colors duration-500",
                        STATUS_STYLE[status],
                      )}
                    >
                      {STATUS_LABEL[status]}
                    </span>
                  </div>
                );
              })}
            </div>
          </div>
        );
      })}
    </div>
  );
}
