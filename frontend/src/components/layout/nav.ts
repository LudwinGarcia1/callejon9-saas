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
 * La barra refleja la matriz de autorizacion del backend, no la sustituye:
 * cada seccion aparece solo para los roles que su @PreAuthorize admite.
 * ADMIN aparece en todas las reglas del flujo -- tomar ordenes es hasAnyRole(
 * 'WAITER','ADMIN'), el tablero de cocina hasAnyRole('KITCHEN','ADMIN') y el
 * cobro hasAnyRole('CASHIER','ADMIN') --, asi que ve todas las secciones.
 * El historial (GET /api/v1/sales y /api/v1/tickets) es de CASHIER y ADMIN, y
 * la analitica (GET /api/v1/analytics) es solo de ADMIN.
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
