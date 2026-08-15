"use client";

import { useQuery } from "@tanstack/react-query";

import { api } from "@/lib/api";
import { endpoints } from "@/lib/endpoints";
import { queryKeys } from "@/lib/query-keys";
import type {
  KitchenOrderResponse,
  OrderSummaryResponse,
  TableResponse,
  UserRole,
} from "@/lib/types";

/** Ordenes que caja puede cobrar: ya pasaron por el mesero. */
const PAYABLE_STATUSES = new Set(["SENT", "READY"]);

/** Roles que el backend deja listar mesas y ordenes (`hasAnyRole` de OrderController). */
const ORDER_ROLES = new Set<UserRole>(["ADMIN", "WAITER", "CASHIER"]);
const KITCHEN_ROLES = new Set<UserRole>(["ADMIN", "KITCHEN"]);

/**
 * Contadores que la navegacion muestra a la derecha de cada seccion (`Mesas
 * 12`, `Cocina 2`, `Caja 4`).
 *
 * Reutiliza las mismas query keys que las pantallas, asi que no genera trafico
 * extra cuando el usuario ya esta parado en una de ellas: comparte cache. Cada
 * consulta se habilita solo para los roles que el backend autoriza, para no
 * provocar 403 innecesarios en la barra.
 */
export function useNavCounts(role: UserRole | undefined): Record<string, number | undefined> {
  const canReadOrders = role !== undefined && ORDER_ROLES.has(role);
  const canReadKitchen = role !== undefined && KITCHEN_ROLES.has(role);

  const tablesQuery = useQuery({
    queryKey: queryKeys.tables.all(),
    queryFn: () => api.get<TableResponse[]>(endpoints.tables.list()),
    enabled: canReadOrders,
  });

  const ordersQuery = useQuery({
    queryKey: queryKeys.orders.all(),
    queryFn: () => api.get<OrderSummaryResponse[]>(endpoints.orders.list()),
    enabled: canReadOrders,
  });

  const kitchenQuery = useQuery({
    queryKey: queryKeys.kitchen.orders(),
    queryFn: () => api.get<KitchenOrderResponse[]>(endpoints.kitchen.orders()),
    enabled: canReadKitchen,
  });

  return {
    "/waiter": tablesQuery.data?.length,
    "/kitchen": kitchenQuery.data?.length,
    "/cashier": ordersQuery.data?.filter((order) => PAYABLE_STATUSES.has(order.status)).length,
  };
}
