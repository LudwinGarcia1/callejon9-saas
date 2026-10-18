/**
 * Los tres planes sembrados en el backend (V1__control_plane.sql). GET
 * /api/v1/platform/plans exige rol SUPER_ADMIN y tanto el alta de restaurante
 * como el sitio público son anónimos, así que esta lista no se puede obtener
 * del servidor: queda fija aquí y es la única copia en el frontend.
 *
 * Solo se publican los límites que el backend impone (PlanLimitService):
 * usuarios y mesas por restaurante. Precios en MXN por mes.
 */
export const PLANS = [
  { code: "FREE", name: "Gratis", priceMonthly: 0, maxUsers: 3, maxTables: 5 },
  { code: "PRO", name: "Profesional", priceMonthly: 499, maxUsers: 15, maxTables: 30 },
  { code: "PREMIUM", name: "Premium", priceMonthly: 999, maxUsers: 60, maxTables: 120 },
] as const;

export type PlanCode = (typeof PLANS)[number]["code"];
