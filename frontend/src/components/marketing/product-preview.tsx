import {
  ChartColumnIcon,
  ChefHatIcon,
  HistoryIcon,
  LayoutGridIcon,
  PackageIcon,
  ReceiptIcon,
  SettingsIcon,
} from "lucide-react";

import { cn } from "@/lib/utils";

import { LogoMark } from "./logo";

/*
 * Representación de la pantalla de mesas del sistema, construida con los
 * mismos módulos, estados y flujo que la app real. Los datos son de ejemplo y
 * todo el bloque es decorativo para lectores de pantalla: el contenido real
 * de la página está en el texto que lo rodea.
 */

type TableState = "free" | "occupied" | "reserved" | "cleaning";

const STATE_LABEL: Record<TableState, string> = {
  free: "Libre",
  occupied: "Ocupada",
  reserved: "Reservada",
  cleaning: "En limpieza",
};

const STATE_DOT: Record<TableState, string> = {
  free: "bg-emerald-600",
  occupied: "bg-mk-orange",
  reserved: "bg-stone-400",
  cleaning: "bg-sky-600",
};

const TABLES: { number: number; seats: number; state: TableState; detail?: string }[] = [
  { number: 1, seats: 4, state: "occupied", detail: "$640.00" },
  { number: 2, seats: 2, state: "free" },
  { number: 3, seats: 4, state: "occupied", detail: "$285.00" },
  { number: 4, seats: 6, state: "occupied", detail: "$1,020.00" },
  { number: 5, seats: 2, state: "reserved", detail: "20:30" },
  { number: 6, seats: 4, state: "free" },
  { number: 7, seats: 4, state: "cleaning" },
  { number: 8, seats: 8, state: "free" },
];

const NAV = [
  { label: "Mesas", icon: LayoutGridIcon, active: true },
  { label: "Cocina", icon: ChefHatIcon },
  { label: "Caja", icon: ReceiptIcon },
  { label: "Historial", icon: HistoryIcon },
  { label: "Inventario", icon: PackageIcon },
  { label: "Analítica", icon: ChartColumnIcon },
  { label: "Administración", icon: SettingsIcon },
];

const ORDER_ITEMS = [
  { qty: 2, name: "Arrachera 300 g", amount: "$640.00", status: "En preparación" },
  { qty: 3, name: "Agua de horchata", amount: "$135.00", status: "Listo" },
  { qty: 1, name: "Guacamole con totopos", amount: "$95.00", status: "Entregado" },
  { qty: 2, name: "Flan napolitano", amount: "$150.00", status: "Pendiente" },
];

export function ProductPreview({ className }: { className?: string }) {
  return (
    <div
      aria-hidden="true"
      className={cn(
        "overflow-hidden rounded-xl border border-mk-border-strong bg-mk-surface text-left shadow-[0_1px_2px_rgba(13,13,13,0.04),0_32px_64px_-32px_rgba(13,13,13,0.28)]",
        className,
      )}
    >
      {/* Barra superior */}
      <div className="flex h-11 items-center justify-between border-b border-mk-border px-4">
        <div className="flex items-center gap-2">
          <LogoMark className="h-6" />
          <span className="text-[13px] font-semibold text-mk-ink">Tu restaurante</span>
        </div>
        <div className="flex items-center gap-3 text-[12px] text-mk-muted">
          <span className="hidden sm:inline">Mesero</span>
          <span className="size-6 rounded-full bg-mk-border" />
        </div>
      </div>

      <div className="flex">
        {/* Navegación */}
        <div className="hidden w-[176px] shrink-0 border-r border-mk-border bg-mk-bg p-2 md:block">
          {NAV.map(({ label, icon: Icon, active }) => (
            <div
              key={label}
              className={cn(
                "flex items-center gap-2.5 rounded-md px-2.5 py-2 text-[13px]",
                active ? "bg-mk-surface font-medium text-mk-ink shadow-[0_0_0_1px_var(--mk-border)]" : "text-mk-muted",
              )}
            >
              <Icon className="size-4" />
              {label}
            </div>
          ))}
        </div>

        {/* Mesas */}
        <div className="min-w-0 flex-1 p-4 sm:p-5">
          <div className="flex flex-wrap items-baseline justify-between gap-2">
            <p className="text-[18px] font-semibold tracking-[-0.01em] text-mk-ink">Mesas</p>
            <p className="font-mono text-[11px] text-mk-muted">8 mesas · 3 libres</p>
          </div>
          <div className="mt-3 flex gap-1.5 text-[12px]">
            {["Todas", "Libres", "Ocupadas"].map((chip, index) => (
              <span
                key={chip}
                className={cn(
                  "rounded-full border px-2.5 py-1",
                  index === 0 ? "border-mk-ink bg-mk-ink text-white" : "border-mk-border text-mk-muted",
                )}
              >
                {chip}
              </span>
            ))}
          </div>

          <div className="mt-4 grid grid-cols-2 gap-2.5 sm:grid-cols-4">
            {TABLES.map((table) => (
              <div
                key={table.number}
                className={cn(
                  "rounded-lg border p-3",
                  table.number === 4
                    ? "border-mk-orange bg-mk-orange-tint shadow-[0_0_0_1px_var(--mk-orange)]"
                    : "border-mk-border bg-mk-surface",
                )}
              >
                <div className="flex items-start justify-between">
                  <span className="font-mono text-[22px] leading-none font-semibold text-mk-ink">
                    {table.number}
                  </span>
                  <span className="text-[11px] text-mk-muted">{table.seats} pers.</span>
                </div>
                <div className="mt-3 flex items-center gap-1.5 text-[11px] text-mk-text">
                  <span className={cn("size-1.5 rounded-full", STATE_DOT[table.state])} />
                  {STATE_LABEL[table.state]}
                </div>
                <p className="mt-0.5 h-4 font-mono text-[11px] text-mk-muted">{table.detail ?? ""}</p>
              </div>
            ))}
          </div>
        </div>

        {/* Orden de la mesa seleccionada */}
        <div className="hidden w-[272px] shrink-0 border-l border-mk-border bg-mk-bg p-4 lg:block">
          <p className="font-mono text-[10px] tracking-[0.12em] text-mk-muted uppercase">Mesa 4 · 6 comensales</p>
          <p className="mt-1 text-[15px] font-semibold text-mk-ink">Orden ORD-0412</p>
          <span className="mt-2 inline-flex items-center gap-1.5 rounded-full border border-mk-orange/60 bg-mk-orange-tint px-2 py-0.5 text-[11px] text-mk-orange-ink">
            <span className="size-1.5 rounded-full bg-mk-orange" />
            Enviada a cocina
          </span>

          <div className="mt-4 divide-y divide-mk-border border-y border-mk-border">
            {ORDER_ITEMS.map((item) => (
              <div key={item.name} className="py-2.5">
                <div className="flex justify-between gap-3 text-[12px]">
                  <span className="text-mk-text">
                    {item.qty} × {item.name}
                  </span>
                  <span className="font-mono text-mk-ink">{item.amount}</span>
                </div>
                <p className="mt-0.5 text-[11px] text-mk-muted">{item.status}</p>
              </div>
            ))}
          </div>

          <div className="mt-3 flex items-baseline justify-between">
            <span className="text-[12px] text-mk-muted">Total</span>
            <span className="font-mono text-[18px] font-semibold text-mk-ink">$1,020.00</span>
          </div>
          <div className="mt-3 rounded-md bg-mk-orange px-3 py-2 text-center text-[12px] font-medium text-mk-ink">
            Agregar productos
          </div>
        </div>
      </div>
    </div>
  );
}
