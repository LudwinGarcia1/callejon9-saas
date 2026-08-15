"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useQuery } from "@tanstack/react-query";

import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { TenantBadge } from "@/components/layout/tenant-badge";
import { ThemeToggle } from "@/components/layout/theme-toggle";
import { NAV_ITEMS_BY_ROLE } from "@/components/layout/nav";
import { Money } from "@/components/shared/money";
import { useNavCounts } from "@/hooks/use-nav-counts";
import { useSession } from "@/hooks/use-session";
import { api } from "@/lib/api";
import { endpoints } from "@/lib/endpoints";
import { queryKeys } from "@/lib/query-keys";
import { USER_ROLE_LABELS, type OrderSummaryResponse, type TableResponse } from "@/lib/types";
import { cn } from "@/lib/utils";

/** Ordenes que siguen abiertas en piso. */
const OPEN_ORDER_STATUSES = new Set(["NEW", "SENT", "READY"]);

/**
 * Barra lateral fija hecha a mano con flex y next/link. Para un rail fijo de
 * un demo de escritorio no vale la pena generar el componente `sidebar` de
 * shadcn, que arrastra `sheet`, `tooltip`, `use-mobile` y un provider
 * completo para un caso que no necesita nada de eso.
 *
 * Desaparece por debajo de 1280px: ahi la navegacion pasa al header
 * horizontal de `AppTopbar`, que es lo que usa el mesero en tablet.
 *
 * Dentro de una comanda el rail cambia de contenido: la navegacion global no
 * sirve mientras se arma una orden, y en cambio si sirve volver a mesas y ver
 * de un vistazo que otras mesas siguen abiertas.
 */
export function AppSidebar() {
  const { user, isLoading, logout } = useSession();
  const pathname = usePathname();
  const orderId = pathname.match(/^\/waiter\/order\/([^/]+)/)?.[1];

  return (
    <aside className="hidden w-[248px] shrink-0 flex-col border-r bg-sidebar px-[18px] py-[26px] xl:flex">
      <TenantBadge
        restaurantName={user?.restaurantName}
        slug={orderId ? undefined : user?.slug}
        isLoading={isLoading}
        className="mb-7"
      />

      {orderId ? <OrderRail currentOrderId={orderId} /> : <NavRail />}

      <div className="mt-[26px] border-t pt-4">
        {isLoading ? (
          <div className="mb-3 flex flex-col gap-1.5">
            <Skeleton className="h-3.5 w-28" />
            <Skeleton className="h-2.5 w-20" />
          </div>
        ) : (
          <>
            <p className="truncate text-[13px] font-medium">{user?.fullName}</p>
            <p className="eyebrow mb-3 truncate tracking-[0.08em]">
              {user ? USER_ROLE_LABELS[user.role] : ""}
            </p>
          </>
        )}
        <ThemeToggle />
        <Button
          variant="outline"
          className="mt-2 h-[34px] w-full text-[13px]"
          onClick={() => logout()}
        >
          Cerrar sesión
        </Button>
      </div>
    </aside>
  );
}

/** Navegacion por rol, con el contador de cada seccion a la derecha. */
function NavRail() {
  const { user } = useSession();
  const pathname = usePathname();
  const navItems = user ? NAV_ITEMS_BY_ROLE[user.role] : [];
  const counts = useNavCounts(user?.role);

  return (
    <nav className="flex flex-1 flex-col gap-0.5">
      {navItems.map((item) => {
        const isActive = pathname.startsWith(item.href);
        const count = counts[item.href];

        return (
          <Link
            key={item.href}
            href={item.href}
            aria-current={isActive ? "page" : undefined}
            className={cn(
              "focus-sala flex items-center justify-between rounded-md border-l-2 p-2.5 text-sm",
              isActive
                ? "border-l-brand bg-sidebar-accent font-medium text-foreground"
                : "border-l-transparent text-muted-foreground hover:bg-sidebar-accent hover:text-foreground",
            )}
          >
            <span>{item.label}</span>
            {count !== undefined && (
              <span className="font-mono text-[11px] text-muted-foreground">{count}</span>
            )}
          </Link>
        );
      })}
    </nav>
  );
}

/** Rail de la comanda: volver a mesas y las demas mesas abiertas del turno. */
function OrderRail({ currentOrderId }: { currentOrderId: string }) {
  const ordersQuery = useQuery({
    queryKey: queryKeys.orders.all(),
    queryFn: () => api.get<OrderSummaryResponse[]>(endpoints.orders.list()),
  });
  const tablesQuery = useQuery({
    queryKey: queryKeys.tables.all(),
    queryFn: () => api.get<TableResponse[]>(endpoints.tables.list()),
  });

  const otherOpenOrders = (ordersQuery.data ?? []).filter(
    (order) => order.id !== currentOrderId && OPEN_ORDER_STATUSES.has(order.status),
  );

  function tableNumber(tableId: string | null): string {
    const table = tablesQuery.data?.find((candidate) => candidate.id === tableId);
    return table ? `Mesa ${table.number}` : "Para llevar";
  }

  return (
    <div className="flex flex-1 flex-col">
      <Link
        href="/waiter"
        className="focus-sala rounded-md border bg-card p-3.5 hover:border-border-strong"
      >
        <span className="eyebrow block">Volver a</span>
        <span className="mt-1 block font-display text-[20px] leading-none">Mesas</span>
      </Link>

      <p className="eyebrow mt-[18px] mb-2.5">Otras mesas abiertas</p>
      {otherOpenOrders.length === 0 ? (
        <p className="text-[13px] text-muted-foreground">Ninguna otra mesa abierta.</p>
      ) : (
        <ul className="flex flex-col gap-2.5">
          {otherOpenOrders.map((order) => (
            <li key={order.id}>
              <Link
                href={`/waiter/order/${order.id}`}
                className="focus-sala flex items-baseline justify-between border-b border-dotted border-border-strong pb-2 text-sm hover:text-brand"
              >
                <span>{tableNumber(order.tableId)}</span>
                <Money amount={order.total} className="font-mono text-xs text-muted-foreground" />
              </Link>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
