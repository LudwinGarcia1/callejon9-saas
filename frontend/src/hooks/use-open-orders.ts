"use client";

import { useQuery } from "@tanstack/react-query";

import { api } from "@/lib/api";
import { endpoints } from "@/lib/endpoints";
import { queryKeys } from "@/lib/query-keys";
import type { OrderSummaryResponse, PageResponse } from "@/lib/types";

/**
 * Tope del backend para `size` (PageQuery.MAX_SIZE). Mesero, caja y barra
 * necesitan TODAS las ordenes abiertas en una sola vista, asi que piden la
 * pagina mas grande permitida.
 */
export const OPEN_ORDERS_PAGE_SIZE = 100;

interface UseOpenOrdersOptions {
  refetchInterval?: number;
  enabled?: boolean;
}

/**
 * Ordenes en servicio (NEW, SENT, READY), la mas reciente primero. Desde
 * CAL-16 `GET /orders` es paginado y filtra en el backend con `open=true`,
 * en vez de traer el historial completo para filtrarlo aqui.
 *
 * Todas las pantallas comparten la misma query key, asi que mesero, caja,
 * barra lateral y contadores leen una sola cache. `isTruncated` avisa si hay
 * mas ordenes abiertas de las que caben en una pagina.
 */
export function useOpenOrders({ refetchInterval, enabled = true }: UseOpenOrdersOptions = {}) {
  const query = useQuery({
    queryKey: queryKeys.orders.open(),
    queryFn: () =>
      api.get<PageResponse<OrderSummaryResponse>>(endpoints.orders.list(), {
        open: true,
        size: OPEN_ORDERS_PAGE_SIZE,
      }),
    refetchInterval,
    enabled,
  });

  return {
    ...query,
    orders: query.data?.content,
    isTruncated: query.data !== undefined && query.data.totalElements > query.data.content.length,
  };
}
