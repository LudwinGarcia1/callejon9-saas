import type { UserRole } from "@/lib/types";

export interface NavItem {
  href: string;
  label: string;
}

const NAV_ADMIN: NavItem = { href: "/admin", label: "Administración" };
const NAV_WAITER: NavItem = { href: "/waiter", label: "Mesas" };
const NAV_KITCHEN: NavItem = { href: "/kitchen", label: "Cocina" };
const NAV_CASHIER: NavItem = { href: "/cashier", label: "Caja" };
const NAV_HISTORY: NavItem = { href: "/history", label: "Historial" };
const NAV_ANALYTICS: NavItem = { href: "/analytics", label: "Analítica" };

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
 * La analitica (GET /api/v1/analytics) tampoco tiene @PreAuthorize -- cualquier
 * autenticado puede consultarla -- pero solo tiene sentido para quien administra
 * el restaurante, asi que la barra la restringe a ADMIN aunque el servidor no
 * lo exija.
 *
 * SUPER_ADMIN solo ve la plataforma porque pertenece al tenant tecnico
 * 'platform', que no tiene mesas, productos ni comandas.
 */
export const NAV_ITEMS_BY_ROLE: Record<UserRole, NavItem[]> = {
  SUPER_ADMIN: [{ href: "/platform", label: "Plataforma" }],
  ADMIN: [NAV_ADMIN, NAV_WAITER, NAV_KITCHEN, NAV_CASHIER, NAV_HISTORY, NAV_ANALYTICS],
  WAITER: [NAV_WAITER],
  KITCHEN: [NAV_KITCHEN],
  CASHIER: [NAV_CASHIER, NAV_HISTORY],
};
