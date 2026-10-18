import Link from "next/link";
import type { ReactNode } from "react";
import {
  ArrowRightIcon,
  ChartColumnIcon,
  CheckIcon,
  ChefHatIcon,
  ChevronDownIcon,
  ClipboardListIcon,
  KeyRoundIcon,
  LayoutGridIcon,
  PackageIcon,
  ReceiptIcon,
  ShieldCheckIcon,
  SmartphoneIcon,
  UsersIcon,
} from "lucide-react";

import { formatCurrency } from "@/lib/format";
import { PLANS } from "@/lib/plans";
import { cn } from "@/lib/utils";

/*
 * Secciones del sitio público. Todo lo que se afirma aquí existe en el
 * sistema (módulos, estados, roles y límites de plan); no hay métricas,
 * clientes ni integraciones de terceros.
 */

export function Container({ children, className }: { children: ReactNode; className?: string }) {
  return <div className={cn("mx-auto max-w-[1160px] px-4 sm:px-6", className)}>{children}</div>;
}

function SectionHeading({
  eyebrow,
  title,
  lead,
  id,
  align = "left",
}: {
  eyebrow: string;
  title: string;
  lead?: string;
  id: string;
  align?: "left" | "center";
}) {
  return (
    <div className={cn("max-w-[640px]", align === "center" && "mx-auto text-center")}>
      <p className="font-mono text-[12px] font-medium tracking-[0.14em] text-mk-orange-ink uppercase">{eyebrow}</p>
      <h2
        id={id}
        className="mt-3 text-[30px] leading-[1.1] font-semibold tracking-[-0.025em] text-balance text-mk-ink sm:text-[38px]"
      >
        {title}
      </h2>
      {lead ? <p className="mt-4 text-[17px] leading-relaxed text-pretty text-mk-muted">{lead}</p> : null}
    </div>
  );
}

/* --- Problemas ----------------------------------------------------------- */

const PROBLEMS = [
  {
    problem: "Comandas en papel que se pierden o no se entienden",
    answer:
      "El mesero captura el pedido en la tableta, el teléfono o la app móvil, y la cocina lo recibe al instante con la mesa y el folio.",
  },
  {
    problem: "La cocina no sabe qué va primero ni qué ya salió",
    answer:
      "El tablero de cocina ordena las comandas por llegada y cada platillo avanza por estados: pendiente, en preparación, listo y entregado.",
  },
  {
    problem: "Cierres de caja e inventario hechos a mano",
    answer:
      "Cada cobro queda registrado con su método de pago y su ticket, y el inventario lleva cada entrada, salida y merma con quién la hizo.",
  },
];

export function ProblemsSection() {
  return (
    <section aria-labelledby="problemas-titulo" className="border-t border-mk-border py-20 sm:py-24">
      <Container>
        <SectionHeading
          id="problemas-titulo"
          eyebrow="El problema"
          title="La operación de un restaurante no cabe en una libreta"
          lead="Cuando el servicio se llena, los errores salen caros: platillos olvidados, cuentas mal cobradas y existencias que nadie sabe."
        />
        <div className="mt-12 grid gap-px overflow-hidden rounded-xl border border-mk-border bg-mk-border md:grid-cols-3">
          {PROBLEMS.map((item) => (
            <div key={item.problem} className="bg-mk-surface p-6 sm:p-7">
              <p className="text-[17px] leading-snug font-semibold text-mk-ink">{item.problem}</p>
              <p className="mt-3 text-[15px] leading-relaxed text-mk-muted">{item.answer}</p>
            </div>
          ))}
        </div>
      </Container>
    </section>
  );
}

/* --- Funcionalidades ----------------------------------------------------- */

const FEATURES = [
  {
    icon: LayoutGridIcon,
    title: "Mesas",
    text: "Plano del salón con el estado de cada mesa: libre, ocupada, reservada o en limpieza.",
  },
  {
    icon: ClipboardListIcon,
    title: "Pedidos",
    text: "Órdenes con folio por mesa y comensales. Se pueden agregar productos mientras la mesa siga abierta.",
  },
  {
    icon: ChefHatIcon,
    title: "Cocina",
    text: "Tablero en modo oscuro para turnos largos, con cada platillo y su estado de preparación.",
  },
  {
    icon: ReceiptIcon,
    title: "Caja",
    text: "Cobro con propina, pago dividido entre varios métodos y ticket descargable en PDF.",
  },
  {
    icon: PackageIcon,
    title: "Inventario",
    text: "Insumos con existencia mínima y aviso cuando bajan, más el registro de cada movimiento.",
  },
  {
    icon: ChartColumnIcon,
    title: "Analítica",
    text: "Ventas por día, productos que concentran la venta y mezcla de métodos de pago.",
  },
  {
    icon: UsersIcon,
    title: "Usuarios y roles",
    text: "Administración, meseros, cocina y caja: cada rol ve solo las pantallas que le corresponden.",
  },
  {
    icon: SmartphoneIcon,
    title: "App para meseros",
    text: "Una app móvil conectada al mismo servidor para consultar mesas y tomar pedidos desde el piso.",
  },
];

export function FeaturesSection() {
  return (
    <section
      id="funcionalidades"
      aria-labelledby="funcionalidades-titulo"
      className="scroll-mt-20 border-t border-mk-border bg-mk-surface py-20 sm:py-24"
    >
      <Container>
        <SectionHeading
          id="funcionalidades-titulo"
          eyebrow="Funcionalidades"
          title="Todo el servicio, conectado de la mesa a la caja"
          lead="Cada módulo trabaja sobre los mismos datos: lo que captura el mesero lo ve la cocina, y lo que cobra la caja alimenta los reportes."
        />
        <div className="mt-12 grid gap-x-10 gap-y-10 sm:grid-cols-2 lg:grid-cols-4">
          {FEATURES.map(({ icon: Icon, title, text }) => (
            <div key={title}>
              <span className="flex size-10 items-center justify-center rounded-lg border border-mk-border bg-mk-bg text-mk-ink">
                <Icon className="size-5" aria-hidden="true" />
              </span>
              <h3 className="mt-4 text-[16px] font-semibold text-mk-ink">{title}</h3>
              <p className="mt-1.5 text-[15px] leading-relaxed text-mk-muted">{text}</p>
            </div>
          ))}
        </div>
      </Container>
    </section>
  );
}

/* --- Flujo de pedidos ---------------------------------------------------- */

const FLOW = [
  {
    step: "01",
    title: "La mesa se abre",
    text: "El mesero elige una mesa libre e indica los comensales. La mesa pasa a ocupada para todo el equipo.",
    states: ["Libre", "Ocupada"],
  },
  {
    step: "02",
    title: "Se captura la comanda",
    text: "Productos y cantidades desde el menú del restaurante, con el total calculado por el sistema.",
    states: ["Nueva"],
  },
  {
    step: "03",
    title: "Cocina prepara",
    text: "La orden llega al tablero de cocina y cada platillo avanza hasta salir de la cocina.",
    states: ["Pendiente", "En preparación", "Listo", "Entregado"],
  },
  {
    step: "04",
    title: "Caja cobra",
    text: "Cobro con propina y pago dividido. Se emite el ticket y la mesa queda libre otra vez.",
    states: ["Pagada"],
  },
];

export function OrderFlowSection() {
  return (
    <section id="flujo" aria-labelledby="flujo-titulo" className="scroll-mt-20 border-t border-mk-border py-20 sm:py-24">
      <Container>
        <SectionHeading
          id="flujo-titulo"
          eyebrow="Flujo de pedidos"
          title="De la mesa a la cocina, sin intermediarios"
          lead="Una orden recorre cuatro pasos y cada persona del equipo ve el mismo estado en su pantalla."
        />
        <ol className="mt-12 grid gap-8 md:grid-cols-4 md:gap-6">
          {FLOW.map((item, index) => (
            <li key={item.step} className="relative">
              <div className="flex items-center gap-3">
                <span className="flex size-9 shrink-0 items-center justify-center rounded-full bg-mk-ink font-mono text-[13px] font-medium text-white">
                  {item.step}
                </span>
                {index < FLOW.length - 1 ? (
                  <span className="hidden h-px flex-1 bg-mk-border-strong md:block" aria-hidden="true" />
                ) : null}
              </div>
              <h3 className="mt-5 text-[17px] font-semibold text-mk-ink">{item.title}</h3>
              <p className="mt-2 text-[15px] leading-relaxed text-mk-muted">{item.text}</p>
              <ul className="mt-4 flex flex-wrap gap-1.5" aria-label="Estados">
                {item.states.map((state) => (
                  <li
                    key={state}
                    className="rounded-full border border-mk-border bg-mk-surface px-2.5 py-1 font-mono text-[11px] text-mk-text"
                  >
                    {state}
                  </li>
                ))}
              </ul>
            </li>
          ))}
        </ol>
      </Container>
    </section>
  );
}

/* --- Inventario y ventas ------------------------------------------------- */

const STOCK_ROWS = [
  { name: "Arrachera", stock: "4.2 kg", min: "5 kg", low: true },
  { name: "Aguacate", stock: "18 pza", min: "10 pza", low: false },
  { name: "Tortilla de maíz", stock: "6.5 kg", min: "3 kg", low: false },
  { name: "Leche", stock: "2 L", min: "4 L", low: true },
];

const MOVEMENTS = [
  { type: "Entrada", item: "Aguacate", qty: "+24 pza" },
  { type: "Merma", item: "Leche", qty: "−1 L" },
  { type: "Salida", item: "Arrachera", qty: "−1.8 kg" },
];

function CheckList({ items }: { items: string[] }) {
  return (
    <ul className="mt-6 space-y-3">
      {items.map((item) => (
        <li key={item} className="flex gap-3 text-[15px] leading-relaxed text-mk-text">
          <CheckIcon className="mt-1 size-4 shrink-0 text-mk-orange-ink" aria-hidden="true" />
          {item}
        </li>
      ))}
    </ul>
  );
}

export function InventorySalesSection() {
  return (
    <section aria-labelledby="inventario-titulo" className="border-t border-mk-border bg-mk-surface py-20 sm:py-24">
      <Container className="grid items-center gap-12 lg:grid-cols-[1fr_1.1fr]">
        <div>
          <SectionHeading
            id="inventario-titulo"
            eyebrow="Inventario y ventas"
            title="Sabe qué tienes y qué vendiste"
            lead="El inventario y la caja dejan de ser hojas sueltas: cada movimiento y cada cobro quedan registrados con fecha y responsable."
          />
          <CheckList
            items={[
              "Existencia mínima por insumo y aviso cuando un insumo llega a ella.",
              "Entradas, salidas, mermas y ajustes con motivo y usuario.",
              "Historial de ventas con folio, método de pago y ticket en PDF.",
              "Pago dividido entre efectivo, tarjeta y transferencia en una misma cuenta.",
            ]}
          />
        </div>

        <div aria-hidden="true" className="overflow-hidden rounded-xl border border-mk-border-strong bg-mk-bg">
          <div className="flex items-center justify-between border-b border-mk-border bg-mk-surface px-5 py-3">
            <p className="text-[14px] font-semibold text-mk-ink">Insumos</p>
            <p className="font-mono text-[11px] text-mk-muted">2 bajo mínimo</p>
          </div>
          <div className="divide-y divide-mk-border bg-mk-surface">
            {STOCK_ROWS.map((row) => (
              <div key={row.name} className="grid grid-cols-[1fr_auto_auto] items-center gap-4 px-5 py-3 text-[13px]">
                <span className="text-mk-text">{row.name}</span>
                <span className="font-mono text-mk-muted">mín. {row.min}</span>
                <span
                  className={cn(
                    "w-[84px] rounded-full px-2 py-0.5 text-center font-mono text-[12px]",
                    row.low ? "bg-mk-orange-tint text-mk-orange-ink" : "text-mk-ink",
                  )}
                >
                  {row.stock}
                </span>
              </div>
            ))}
          </div>
          <div className="border-t border-mk-border px-5 py-4">
            <p className="font-mono text-[10px] tracking-[0.12em] text-mk-muted uppercase">Últimos movimientos</p>
            <div className="mt-3 space-y-2">
              {MOVEMENTS.map((movement) => (
                <div key={movement.item + movement.type} className="flex items-center justify-between text-[13px]">
                  <span className="text-mk-text">
                    <span className="mr-2 inline-block w-[60px] text-mk-muted">{movement.type}</span>
                    {movement.item}
                  </span>
                  <span className="font-mono text-mk-ink">{movement.qty}</span>
                </div>
              ))}
            </div>
          </div>
        </div>
      </Container>
    </section>
  );
}

/* --- Reportes ------------------------------------------------------------ */

const DAILY = [38, 52, 46, 61, 74, 92, 68];
const DAYS = ["Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom"];
const MIX = [
  { label: "Tarjeta", share: 54 },
  { label: "Efectivo", share: 31 },
  { label: "Transferencia", share: 15 },
];

export function ReportsSection() {
  return (
    <section aria-labelledby="reportes-titulo" className="border-t border-mk-border py-20 sm:py-24">
      <Container className="grid items-center gap-12 lg:grid-cols-[1.1fr_1fr]">
        <div aria-hidden="true" className="order-last rounded-xl border border-mk-border-strong bg-mk-surface p-5 sm:p-6 lg:order-first">
          <div className="flex items-baseline justify-between">
            <p className="text-[14px] font-semibold text-mk-ink">Ventas por día</p>
            <p className="font-mono text-[11px] text-mk-muted">Últimos 7 días · ejemplo</p>
          </div>
          <div className="mt-6 flex h-40 gap-3">
            {DAILY.map((value, index) => (
              <div key={DAYS[index]} className="flex h-full flex-1 flex-col items-center gap-2">
                {/* La barra se mide contra este contenedor, que sí tiene altura. */}
                <div className="relative w-full flex-1">
                  <div
                    className={cn(
                      "absolute inset-x-0 bottom-0 rounded-t-[4px]",
                      index === 5 ? "bg-mk-orange" : "bg-mk-ink/85",
                    )}
                    style={{ height: `${value}%` }}
                  />
                </div>
                <span className="font-mono text-[10px] text-mk-muted">{DAYS[index]}</span>
              </div>
            ))}
          </div>
          <div className="mt-6 border-t border-mk-border pt-5">
            <p className="font-mono text-[10px] tracking-[0.12em] text-mk-muted uppercase">Métodos de pago</p>
            <div className="mt-3 flex h-2.5 overflow-hidden rounded-full">
              <span className="bg-mk-ink" style={{ width: `${MIX[0].share}%` }} />
              <span className="bg-mk-orange" style={{ width: `${MIX[1].share}%` }} />
              <span className="bg-mk-border-strong" style={{ width: `${MIX[2].share}%` }} />
            </div>
            <div className="mt-3 flex flex-wrap gap-x-5 gap-y-1 text-[12px] text-mk-muted">
              {MIX.map((item, index) => (
                <span key={item.label} className="flex items-center gap-1.5">
                  <span
                    className={cn(
                      "size-2 rounded-full",
                      ["bg-mk-ink", "bg-mk-orange", "bg-mk-border-strong"][index],
                    )}
                  />
                  {item.label}
                </span>
              ))}
            </div>
          </div>
        </div>

        <div>
          <SectionHeading
            id="reportes-titulo"
            eyebrow="Reportes y análisis"
            title="Decide con lo que realmente pasó en tu servicio"
            lead="La analítica se calcula a partir de los cobros registrados, sin capturas extra ni hojas de cálculo."
          />
          <CheckList
            items={[
              "Ventas por día en el rango de fechas que elijas.",
              "Productos que concentran la mayor parte de la venta (análisis de Pareto).",
              "Mezcla de métodos de pago para cuadrar la caja.",
              "Historial de órdenes y búsqueda de tickets por folio.",
            ]}
          />
        </div>
      </Container>
    </section>
  );
}

/* --- Beneficios ---------------------------------------------------------- */

const BENEFITS = [
  {
    audience: "Para propietarios",
    items: [
      "Ves la operación de mesas, cocina y caja desde cualquier navegador.",
      "Controlas quién entra y qué puede hacer con usuarios por rol.",
      "Tus datos están separados de los de otros restaurantes en la propia base de datos.",
      "Inventario y ventas registrados para revisar el negocio con calma.",
    ],
  },
  {
    audience: "Para el equipo",
    items: [
      "Meseros: menos viajes a la cocina y la cuenta de cada mesa siempre a la vista.",
      "Cocina: una sola pantalla con lo pendiente, en el orden en que llegó.",
      "Caja: el total ya calculado, con propina y pago dividido.",
      "Pantallas pensadas para usarse rápido durante el servicio.",
    ],
  },
];

export function BenefitsSection() {
  return (
    <section
      id="beneficios"
      aria-labelledby="beneficios-titulo"
      className="scroll-mt-20 border-t border-mk-border bg-mk-surface py-20 sm:py-24"
    >
      <Container>
        <SectionHeading
          id="beneficios-titulo"
          eyebrow="Beneficios"
          title="Menos fricción para quien dirige y para quien atiende"
        />
        <div className="mt-12 grid gap-6 md:grid-cols-2">
          {BENEFITS.map((group) => (
            <div key={group.audience} className="rounded-xl border border-mk-border bg-mk-bg p-6 sm:p-8">
              <h3 className="text-[19px] font-semibold text-mk-ink">{group.audience}</h3>
              <CheckList items={group.items} />
            </div>
          ))}
        </div>
        <div className="mt-6 flex flex-col gap-4 rounded-xl border border-mk-border p-6 sm:flex-row sm:items-center sm:gap-6 sm:p-8">
          <span className="flex size-11 shrink-0 items-center justify-center rounded-lg bg-mk-ink text-white">
            <ShieldCheckIcon className="size-5" aria-hidden="true" />
          </span>
          <div className="flex-1">
            <h3 className="text-[16px] font-semibold text-mk-ink">Seguridad desde la base</h3>
            <p className="mt-1 text-[15px] leading-relaxed text-mk-muted">
              Cada restaurante solo puede leer sus propios datos, regla que aplica la base de datos y no solo la
              aplicación. La sesión viaja en cookies seguras que el navegador no expone al código de la página.
            </p>
          </div>
          <KeyRoundIcon className="hidden size-5 text-mk-muted sm:block" aria-hidden="true" />
        </div>
      </Container>
    </section>
  );
}

/* --- Precios ------------------------------------------------------------- */

export function PricingSection() {
  return (
    <section id="precios" aria-labelledby="precios-titulo" className="scroll-mt-20 border-t border-mk-border py-20 sm:py-24">
      <Container>
        <SectionHeading
          id="precios-titulo"
          eyebrow="Precios"
          title="Un plan para cada tamaño de restaurante"
          lead="Todos los planes incluyen todos los módulos. Cambia el número de usuarios y de mesas que puedes dar de alta."
          align="center"
        />
        <div className="mx-auto mt-12 grid max-w-[980px] gap-4 md:grid-cols-3">
          {PLANS.map((plan) => {
            const featured = plan.code === "PRO";
            return (
              <div
                key={plan.code}
                className={cn(
                  "flex flex-col rounded-xl border bg-mk-surface p-6",
                  featured ? "border-mk-ink shadow-[0_0_0_1px_var(--mk-ink)]" : "border-mk-border",
                )}
              >
                <div className="flex items-center justify-between">
                  <h3 className="text-[17px] font-semibold text-mk-ink">{plan.name}</h3>
                  {featured ? (
                    <span className="rounded-full bg-mk-orange px-2.5 py-0.5 text-[11px] font-medium text-mk-ink">
                      Recomendado
                    </span>
                  ) : null}
                </div>
                <p className="mt-5 flex items-baseline gap-1.5">
                  <span className="font-mono text-[34px] font-semibold tracking-[-0.02em] text-mk-ink">
                    {formatCurrency(plan.priceMonthly)}
                  </span>
                  <span className="text-[14px] text-mk-muted">MXN / mes</span>
                </p>
                <ul className="mt-6 space-y-2.5 border-t border-mk-border pt-6 text-[15px] text-mk-text">
                  <li className="flex gap-2.5">
                    <CheckIcon className="mt-1 size-4 text-mk-orange-ink" aria-hidden="true" />
                    Hasta {plan.maxUsers} usuarios
                  </li>
                  <li className="flex gap-2.5">
                    <CheckIcon className="mt-1 size-4 text-mk-orange-ink" aria-hidden="true" />
                    Hasta {plan.maxTables} mesas
                  </li>
                  <li className="flex gap-2.5">
                    <CheckIcon className="mt-1 size-4 text-mk-orange-ink" aria-hidden="true" />
                    Todos los módulos
                  </li>
                </ul>
                <Link
                  href="/signup"
                  className={cn(
                    "mt-8 rounded-md px-4 py-2.5 text-center text-sm font-medium transition-colors",
                    featured
                      ? "bg-mk-ink text-white hover:bg-mk-ink/85"
                      : "border border-mk-border-strong text-mk-ink hover:bg-mk-bg",
                  )}
                >
                  {plan.priceMonthly === 0 ? "Empezar gratis" : `Elegir ${plan.name}`}
                </Link>
              </div>
            );
          })}
        </div>
      </Container>
    </section>
  );
}

/* --- Preguntas frecuentes ------------------------------------------------ */

const FAQ = [
  {
    q: "¿Necesito instalar algo?",
    a: "No. El sistema funciona en el navegador de una computadora, tableta o teléfono. Los meseros pueden usar además la app móvil, conectada al mismo servidor.",
  },
  {
    q: "¿Qué roles puedo dar de alta?",
    a: "Administración, mesero, cocina y caja. Cada rol entra solo a sus pantallas y el servidor vuelve a comprobar el permiso en cada operación.",
  },
  {
    q: "¿Mis datos están separados de los de otros restaurantes?",
    a: "Sí. La separación la impone la base de datos: aunque hubiera un error en la aplicación, una consulta de tu restaurante no puede leer datos de otro.",
  },
  {
    q: "¿Qué pasa si un cliente pide algo más después de ordenar?",
    a: "La orden sigue abierta hasta que se cobra, así que el mesero puede agregar productos y el total se recalcula.",
  },
  {
    q: "¿Cómo se cobra una cuenta entre varias personas?",
    a: "La caja puede dividir el pago entre varios métodos y agregar propina. Al cobrar se emite el ticket, que se puede descargar en PDF.",
  },
  {
    q: "¿Cuánto cuesta empezar?",
    a: "El plan Gratis no tiene costo e incluye 3 usuarios y 5 mesas. Los planes Profesional y Premium amplían esos límites.",
  },
];

export function FaqSection() {
  return (
    <section
      id="preguntas"
      aria-labelledby="preguntas-titulo"
      className="scroll-mt-20 border-t border-mk-border bg-mk-surface py-20 sm:py-24"
    >
      <Container className="grid gap-12 lg:grid-cols-[0.8fr_1.2fr]">
        <SectionHeading id="preguntas-titulo" eyebrow="Preguntas frecuentes" title="Lo que suelen preguntarnos" />
        <div className="divide-y divide-mk-border border-y border-mk-border">
          {FAQ.map((item) => (
            <details key={item.q} className="group py-1">
              <summary className="flex cursor-pointer list-none items-center justify-between gap-6 py-4 text-[16px] font-medium text-mk-ink [&::-webkit-details-marker]:hidden">
                {item.q}
                <ChevronDownIcon
                  className="size-4 shrink-0 text-mk-muted transition-transform group-open:rotate-180 motion-reduce:transition-none"
                  aria-hidden="true"
                />
              </summary>
              <p className="pr-10 pb-5 text-[15px] leading-relaxed text-mk-muted">{item.a}</p>
            </details>
          ))}
        </div>
      </Container>
    </section>
  );
}

/* --- CTA final ----------------------------------------------------------- */

export function FinalCtaSection() {
  return (
    <section aria-labelledby="cta-titulo" className="py-20 sm:py-24">
      <Container>
        <div className="relative overflow-hidden rounded-2xl bg-mk-ink px-6 py-14 text-center sm:px-12 sm:py-16">
          <span className="absolute inset-x-0 top-0 h-1 bg-mk-orange" aria-hidden="true" />
          <h2
            id="cta-titulo"
            className="mx-auto max-w-[620px] text-[30px] leading-[1.1] font-semibold tracking-[-0.025em] text-balance text-white sm:text-[40px]"
          >
            Pon tu restaurante en orden desde el próximo servicio
          </h2>
          <p className="mx-auto mt-4 max-w-[520px] text-[17px] leading-relaxed text-white/70">
            Crea tu cuenta, da de alta tus mesas y tu menú, e invita a tu equipo.
          </p>
          <div className="mt-8 flex flex-col justify-center gap-3 sm:flex-row">
            <Link
              href="/signup"
              className="inline-flex items-center justify-center gap-2 rounded-md bg-mk-orange px-5 py-3 text-[15px] font-medium text-mk-ink transition-colors hover:bg-mk-orange-2"
            >
              Crear mi restaurante
              <ArrowRightIcon className="size-4" aria-hidden="true" />
            </Link>
            <Link
              href="/login"
              className="inline-flex items-center justify-center rounded-md border border-white/25 px-5 py-3 text-[15px] font-medium text-white transition-colors hover:bg-white/10"
            >
              Iniciar sesión
            </Link>
          </div>
        </div>
      </Container>
    </section>
  );
}
