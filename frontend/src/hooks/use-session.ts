"use client";

import { useMutation, useQuery } from "@tanstack/react-query";
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
 */
export function useSession() {
  const router = useRouter();

  const sessionQuery = useQuery({
    queryKey: queryKeys.session.me(),
    queryFn: () => api.get<SessionResponse>(endpoints.auth.me()),
    retry: false,
  });

  const logoutMutation = useMutation({
    mutationFn: () => api.post<void>(endpoints.auth.logout()),
    // `replace` y no `push`: la pantalla de la que se sale no queda en el
    // historial, asi que Atras no regresa a ella (CAL-194).
    //
    // El cache no se limpia aqui sino en `providers.tsx`, al llegar al login:
    // limpiarlo con la pantalla aun montada hacia que sus consultas volvieran a
    // pedir, recibieran 401 y se tomaran por una sesion expirada.
    onSettled: () => {
      router.replace("/login");
    },
  });

  return {
    user: sessionQuery.data,
    isLoading: sessionQuery.isLoading,
    error: sessionQuery.error,
    logout: logoutMutation.mutate,
  };
}
