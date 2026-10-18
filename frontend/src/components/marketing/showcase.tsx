"use client";

import { useRef, useState } from "react";
import type { KeyboardEvent, ReactNode } from "react";
import { CheckIcon, ClipboardListIcon, ReceiptIcon, SmartphoneIcon, UsersIcon } from "lucide-react";

import { cn } from "@/lib/utils";

import { PhonePreview } from "./phone-preview";

/*
 * Vitrina del producto: una pestaña por pantalla del sistema, cada una con su
 * réplica. Las réplicas son decorativas (aria-hidden); lo que describen está
 * en el texto de cada panel.
 */

function Frame({ title, meta, children }: { title: string; meta?: string; children: ReactNode }) {
  return (
    <div
      aria-hidden="true"
      className="overflow-hidden rounded-xl border border-mk-border-strong bg-mk-surface shadow-[0_1px_2px_rgba(13,13,13,0.04),0_28px_56px_-32px_rgba(13,13,13,0.3)]"
    >
      <div className="flex items-center justify-between border-b border-mk-border px-5 py-3">
        <p className="text-[14px] font-semibold text-mk-ink">{title}</p>
        {meta ? <p className="font-mono text-[11px] text-mk-muted">{meta}</p> : null}
      </div>
      {children}
    </div>
  );
}

function OrderMock() {
  const products = [
    { name: "Guacamole con totopos", price: "$95.00", qty: 1 },
    { name: "Queso fundido", price: "$110.00", qty: 0 },
    { name: "Sopa de tortilla", price: "$85.00", qty: 2 },
  ];
  return (
    <Frame title="Mesa 6 · Orden ORD-0418" meta="Nueva">
      <div className="grid sm:grid-cols-[1fr_1.1fr]">
        <div className="border-b border-mk-border p-5 sm:border-r sm:border-b-0">
          <p className="font-mono text-[10px] tracking-[0.12em] text-mk-muted uppercase">Comanda</p>
          <div className="mt-3 space-y-2.5 text-[13px]">
            {[
              ["1 × Guacamole con totopos", "$95.00"],
              ["2 × Sopa de tortilla", "$170.00"],
              ["2 × Agua de horchata", "$90.00"],
            ].map(([name, amount]) => (
              <div key={name} className="flex justify-between gap-3">
                <span className="text-mk-text">{name}</span>
                <span className="font-mono text-mk-ink">{amount}</span>
              </div>
            ))}
          </div>
          <div className="mt-4 flex items-baseline justify-between border-t border-mk-border pt-3">
            <span className="text-[12px] text-mk-muted">Total</span>
            <span className="font-mono text-[18px] font-semibold text-mk-ink">$355.00</span>
          </div>
          <div className="mt-4 rounded-md bg-mk-ink py-2 text-center text-[12px] font-medium text-white">
            Enviar a cocina
          </div>
        </div>
        <div className="bg-mk-bg p-5">
          <div className="flex flex-wrap gap-1.5 text-[11px]">
            {["Entradas", "Platos fuertes", "Bebidas", "Postres"].map((chip, index) => (
              <span
                key={chip}
                className={cn(
                  "rounded-full border px-2.5 py-1",
                  index === 0 ? "border-mk-ink bg-mk-ink text-white" : "border-mk-border bg-mk-surface text-mk-muted",
                )}
              >
                {chip}
              </span>
            ))}
          </div>
          <div className="mt-3 space-y-2">
            {products.map((product) => (
              <div
                key={product.name}
                className="flex items-center justify-between rounded-lg border border-mk-border bg-mk-surface px-3 py-2.5"
              >
                <div>
                  <p className="text-[12.5px] font-medium text-mk-ink">{product.name}</p>
                  <p className="font-mono text-[11px] text-mk-muted">{product.price}</p>
                </div>
                <div className="flex items-center gap-1.5">
                  <span className="flex size-7 items-center justify-center rounded-md border border-mk-border text-mk-ink">−</span>
                  <span className="w-4 text-center font-mono text-[12px] text-mk-ink">{product.qty}</span>
                  <span className="flex size-7 items-center justify-center rounded-md border border-mk-border text-mk-ink">+</span>
                </div>
              </div>
            ))}
          </div>
        </div>
      </div>
    </Frame>
  );
}

function CheckoutMock() {
  return (
    <Frame title="Caja · Mesa 4" meta="ORD-0412">
      <div className="grid gap-0 sm:grid-cols-[1fr_1fr]">
        <div className="border-b border-mk-border p-5 sm:border-r sm:border-b-0">
          {[
            ["Subtotal", "$1,020.00"],
            ["Propina", "$153.00"],
          ].map(([label, amount]) => (
            <div key={label} className="flex justify-between py-1 text-[13px]">
              <span className="text-mk-muted">{label}</span>
              <span className="font-mono text-mk-ink">{amount}</span>
            </div>
          ))}
          <div className="mt-3">
            <p className="text-[11px] text-mk-muted">Propina (%)</p>
            <div className="mt-1 rounded-md border border-mk-border-strong bg-mk-surface px-3 py-1.5 font-mono text-[13px] text-mk-ink">
              15
            </div>
          </div>
          <div className="mt-4 flex items-baseline justify-between border-t border-mk-border pt-3">
            <span className="text-[12px] text-mk-muted">Total a cobrar</span>
            <span className="font-mono text-[22px] font-semibold text-mk-ink">$1,173.00</span>
          </div>
        </div>
        <div className="bg-mk-bg p-5">
          <p className="font-mono text-[10px] tracking-[0.12em] text-mk-muted uppercase">Pago dividido</p>
          <div className="mt-3 space-y-2">
            {[
              ["Efectivo", "$600.00"],
              ["Tarjeta", "$573.00"],
            ].map(([method, amount]) => (
              <div
                key={method}
                className="flex items-center justify-between rounded-lg border border-mk-border bg-mk-surface px-3 py-2.5 text-[13px]"
              >
                <span className="text-mk-text">{method}</span>
                <span className="font-mono text-mk-ink">{amount}</span>
              </div>
            ))}
          </div>
          <div className="mt-3 flex justify-between text-[12px]">
            <span className="text-mk-muted">Restante</span>
            <span className="font-mono text-emerald-700">$0.00</span>
          </div>
          <div className="mt-4 rounded-md bg-mk-orange py-2 text-center text-[12px] font-medium text-mk-ink">
            Confirmar cobro
          </div>
        </div>
      </div>
    </Frame>
  );
}

function TeamMock() {
  const users = [
    { name: "Administración", role: "Administración", tone: "bg-mk-ink text-white" },
    { name: "Mesero de piso", role: "Mesero", tone: "bg-mk-orange-tint text-mk-orange-ink" },
    { name: "Mesera de terraza", role: "Mesero", tone: "bg-mk-orange-tint text-mk-orange-ink" },
    { name: "Jefe de cocina", role: "Cocina", tone: "bg-stone-100 text-stone-700" },
    { name: "Cajero de turno", role: "Caja", tone: "bg-emerald-50 text-emerald-800" },
  ];
  return (
    <Frame title="Usuarios" meta="5 de 15 · plan Profesional">
      <div className="divide-y divide-mk-border">
        {users.map((user) => (
          <div key={user.name} className="flex items-center justify-between px-5 py-3">
            <div className="flex items-center gap-3">
              <span className="flex size-8 items-center justify-center rounded-full bg-mk-bg font-mono text-[11px] text-mk-muted">
                {user.name
                  .split(" ")
                  .map((word) => word[0])
                  .slice(0, 2)
                  .join("")}
              </span>
              <span className="text-[13px] text-mk-ink">{user.name}</span>
            </div>
            <span className={cn("rounded-full px-2.5 py-0.5 text-[11px] font-medium", user.tone)}>{user.role}</span>
          </div>
        ))}
      </div>
      <div className="border-t border-mk-border bg-mk-bg px-5 py-3 text-[12px] text-mk-muted">
        Cada rol entra solo a sus pantallas; el servidor vuelve a comprobarlo en cada operación.
      </div>
    </Frame>
  );
}

function MobileMock() {
  return (
    <div className="flex justify-center rounded-xl border border-mk-border bg-[radial-gradient(ellipse_at_center,var(--mk-orange-tint),transparent_70%)] py-8">
      <PhonePreview />
    </div>
  );
}

const TABS = [
  {
    id: "comandas",
    label: "Comandas",
    icon: ClipboardListIcon,
    title: "Toma el pedido en segundos",
    text: "El menú se filtra por categoría y la comanda se arma con un toque. El total lo calcula el sistema y la orden sigue abierta para agregar lo que se pida después.",
    points: ["Folio por orden y comensales por mesa", "Productos agrupados por categoría", "Envío a cocina con un botón"],
    mock: <OrderMock />,
  },
  {
    id: "caja",
    label: "Caja",
    icon: ReceiptIcon,
    title: "Cobra sin calculadora",
    text: "La caja recibe la cuenta lista. Indica la propina, reparte el pago entre varios métodos y confirma el cobro: se emite el ticket y la mesa queda libre.",
    points: ["Propina en el porcentaje que el cliente elija", "Pago dividido entre efectivo, tarjeta y transferencia", "Ticket descargable en PDF"],
    mock: <CheckoutMock />,
  },
  {
    id: "equipo",
    label: "Equipo",
    icon: UsersIcon,
    title: "Cada quien, en su pantalla",
    text: "Da de alta a tu equipo con su rol. Meseros, cocina y caja ven solo lo suyo, y la administración ve todo el restaurante.",
    points: ["Cuatro roles: administración, mesero, cocina y caja", "Límite de usuarios según tu plan", "Permisos comprobados en el servidor"],
    mock: <TeamMock />,
  },
  {
    id: "movil",
    label: "App móvil",
    icon: SmartphoneIcon,
    title: "El salón en el bolsillo del mesero",
    text: "Con la app móvil, el mesero consulta las mesas, abre órdenes y agrega productos desde el piso, conectado al mismo servidor que la web.",
    points: ["Mesas y menú en tiempo real", "Agregar productos a una mesa ocupada", "Conexión cifrada y sesión segura"],
    mock: <MobileMock />,
  },
];

export function Showcase() {
  const [active, setActive] = useState(0);
  const tabRefs = useRef<(HTMLButtonElement | null)[]>([]);

  function onKeyDown(event: KeyboardEvent<HTMLDivElement>) {
    const keys: Record<string, number> = { ArrowRight: 1, ArrowLeft: -1 };
    if (!(event.key in keys) && event.key !== "Home" && event.key !== "End") return;
    event.preventDefault();
    let next = active;
    if (event.key === "Home") next = 0;
    else if (event.key === "End") next = TABS.length - 1;
    else next = (active + keys[event.key] + TABS.length) % TABS.length;
    setActive(next);
    tabRefs.current[next]?.focus();
  }

  const tab = TABS[active];

  return (
    <div className="mt-12">
      <div
        role="tablist"
        aria-label="Pantallas del sistema"
        onKeyDown={onKeyDown}
        className="grid grid-cols-2 gap-1 rounded-xl border border-mk-border bg-mk-surface p-1 sm:inline-grid sm:grid-flow-col sm:grid-cols-none"
      >
        {TABS.map(({ id, label, icon: Icon }, index) => {
          const selected = index === active;
          return (
            <button
              key={id}
              ref={(element) => {
                tabRefs.current[index] = element;
              }}
              id={`tab-${id}`}
              type="button"
              role="tab"
              aria-selected={selected}
              aria-controls={`panel-${id}`}
              tabIndex={selected ? 0 : -1}
              onClick={() => setActive(index)}
              className={cn(
                "flex cursor-pointer items-center justify-center gap-2 rounded-lg px-3.5 py-2.5 text-[14px] font-medium transition-colors sm:py-2",
                selected ? "bg-mk-ink text-white" : "text-mk-muted hover:bg-mk-bg hover:text-mk-ink",
              )}
            >
              <Icon className="size-4" aria-hidden="true" />
              {label}
            </button>
          );
        })}
      </div>

      <div
        key={tab.id}
        id={`panel-${tab.id}`}
        role="tabpanel"
        aria-labelledby={`tab-${tab.id}`}
        className="mk-panel mt-8 grid items-center gap-8 lg:grid-cols-[0.8fr_1.2fr] lg:gap-10"
      >
        <div>
          <h3 className="text-[24px] leading-tight font-semibold tracking-[-0.02em] text-mk-ink sm:text-[28px]">
            {tab.title}
          </h3>
          <p className="mt-3 text-[16px] leading-relaxed text-mk-muted">{tab.text}</p>
          <ul className="mt-6 space-y-3">
            {tab.points.map((point) => (
              <li key={point} className="flex gap-3 text-[15px] text-mk-text">
                <CheckIcon className="mt-0.5 size-4 shrink-0 text-mk-orange-ink" aria-hidden="true" />
                {point}
              </li>
            ))}
          </ul>
        </div>
        <div>{tab.mock}</div>
      </div>
    </div>
  );
}
