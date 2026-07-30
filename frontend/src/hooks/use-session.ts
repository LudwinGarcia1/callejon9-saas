"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useRouter } from "next/navigation";

import { api } from "@/lib/api";
import { endpoints } from "@/lib/endpoints";
import { queryKeys } from "@/lib/query-keys";
import type { SessionResponse, UserRole } from "@/lib/types";

/** Ruta de aterrizaje segun el rol del usuario autenticado. */
export const ROLE_LANDING_PATH: Record<UserRole, string> = {
  SUPER_ADMIN: "/platform",
  ADMIN: "/admin",
  WAITER: "/waiter",
  KITCHEN: "/kitchen",
  CASHIER: "/cashier",
};

/**
 * Sesion del usuario autenticado, leida de GET /api/v1/auth/me.
 *
 * Ese endpoint todavia no existe en el backend — se esta agregando en
 * paralelo en otro workstream — asi que mientras tanto esta consulta
 * responde 404. El hook queda escrito contra el contrato esperado
 * (`SessionResponse`) para no bloquear el resto del frontend.
 */
export function useSession() {
  const queryClient = useQueryClient();
  const router = useRouter();

  const sessionQuery = useQuery({
    queryKey: queryKeys.session.me(),
    queryFn: () => api.get<SessionResponse>(endpoints.auth.me()),
    retry: false,
  });

  const logoutMutation = useMutation({
    mutationFn: () => api.post<void>(endpoints.auth.logout()),
    onSettled: () => {
      queryClient.clear();
      router.push("/login");
    },
  });

  return {
    user: sessionQuery.data,
    isLoading: sessionQuery.isLoading,
    logout: logoutMutation.mutate,
  };
}
