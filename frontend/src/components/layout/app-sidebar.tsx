"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";

import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { useSession } from "@/hooks/use-session";
import { cn } from "@/lib/utils";
import type { UserRole } from "@/lib/types";

const NAV_ADMIN = { href: "/admin", label: "Administracion" };
const NAV_WAITER = { href: "/waiter", label: "Mesas" };
const NAV_KITCHEN = { href: "/kitchen", label: "Cocina" };
const NAV_CASHIER = { href: "/cashier", label: "Caja" };
const NAV_HISTORY = { href: "/history", label: "Historial" };

/**
 * Navegacion disponible por rol.
 *
 * ADMIN ve las cinco secciones operativas, y no es una concesion: en el
 * backend ADMIN aparece en todos los @PreAuthorize del flujo -- ordenes son
 * hasAnyRole('WAITER','ADMIN'), el tablero de cocina es hasAnyRole('KITCHEN',
 * 'ADMIN') y el cobro es hasAnyRole('CASHIER','ADMIN'). La barra refleja la
 * autoridad real en vez de inventar una restriccion que el servidor no aplica.
 * El historial de ventas (GET /api/v1/sales) no tiene @PreAuthorize -- cualquier
 * autenticado puede consultarlo -- pero solo tiene sentido operativo para quien
 * cobra (CASHIER) o administra el restaurante (ADMIN).
 *
 * SUPER_ADMIN solo ve la plataforma porque pertenece al tenant tecnico
 * 'platform', que no tiene mesas, productos ni comandas.
 */
const NAV_ITEMS_BY_ROLE: Record<UserRole, { href: string; label: string }[]> = {
  SUPER_ADMIN: [{ href: "/platform", label: "Plataforma" }],
  ADMIN: [NAV_ADMIN, NAV_WAITER, NAV_KITCHEN, NAV_CASHIER, NAV_HISTORY],
  WAITER: [NAV_WAITER],
  KITCHEN: [NAV_KITCHEN],
  CASHIER: [NAV_CASHIER, NAV_HISTORY],
};

/**
 * Barra lateral fija hecha a mano con flex y next/link. Para un rail fijo de
 * un demo de escritorio no vale la pena generar el componente `sidebar` de
 * shadcn, que arrastra `sheet`, `tooltip`, `use-mobile` y un provider
 * completo para un caso que no necesita nada de eso.
 */
export function AppSidebar() {
  const { user, isLoading, logout } = useSession();
  const pathname = usePathname();
  const navItems = user ? NAV_ITEMS_BY_ROLE[user.role] : [];

  return (
    <aside className="flex w-64 shrink-0 flex-col border-r p-4">
      <div className="mb-6">
        {isLoading ? (
          <Skeleton className="h-5 w-32" />
        ) : (
          <p className="font-semibold">
            {user?.restaurantName ?? "Callejon 9"}
          </p>
        )}
      </div>

      <nav className="flex flex-1 flex-col gap-1">
        {navItems.map((item) => {
          const isActive = pathname.startsWith(item.href);
          return (
            <Link
              key={item.href}
              href={item.href}
              className={cn(
                "rounded-md px-3 py-2 text-sm transition-colors",
                isActive
                  ? "bg-muted font-medium text-foreground"
                  : "text-muted-foreground hover:bg-muted hover:text-foreground",
              )}
            >
              {item.label}
            </Link>
          );
        })}
      </nav>

      <div className="mt-6 border-t pt-4">
        {isLoading ? (
          <Skeleton className="mb-2 h-4 w-24" />
        ) : (
          <p className="mb-2 truncate text-sm text-muted-foreground">
            {user?.fullName}
          </p>
        )}
        <Button
          variant="outline"
          size="sm"
          className="w-full"
          onClick={() => logout()}
        >
          Cerrar sesion
        </Button>
      </div>
    </aside>
  );
}
