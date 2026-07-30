"use client";

import {
  MutationCache,
  QueryCache,
  QueryClient,
  QueryClientProvider,
} from "@tanstack/react-query";
import { useRouter } from "next/navigation";
import { useState } from "react";

import { ApiError } from "@/lib/api";

/**
 * Reintentar una peticion que fallo por sesion expirada o sin permiso (401 o
 * 403) solo retrasa lo inevitable; para cualquier otro error un reintento es
 * suficiente en una demo con red estable.
 */
function shouldRetry(failureCount: number, error: unknown): boolean {
  if (error instanceof ApiError && (error.status === 401 || error.status === 403)) {
    return false;
  }
  return failureCount < 1;
}

/** Provee un unico QueryClient por sesion de navegador a todo el arbol. */
export function Providers({ children }: { children: React.ReactNode }) {
  const router = useRouter();

  const [queryClient] = useState(() => {
    /**
     * Punto unico de manejo de perdida de sesion: cualquier query o
     * mutation que falle con 401 significa que la cookie `access_token`
     * expiro o ya no es valida, asi que limpiamos todo el cache y
     * redirigimos a login. Un 403 es distinto — significa rol equivocado o
     * falta de contexto de tenant (`NoTenantContextException`, que el
     * backend mapea a 403) y NO debe redirigir a login: eso crearia un
     * ciclo de "inicia sesion correctamente -> rebota de inmediato". Ese
     * caso lo debe renderizar la pantalla que hizo la peticion, como un
     * estado de "sin permiso".
     */
    const client: QueryClient = new QueryClient({
      queryCache: new QueryCache({
        onError: (error) => {
          if (error instanceof ApiError && error.status === 401) {
            client.clear();
            router.replace("/login?expired=1");
          }
        },
      }),
      mutationCache: new MutationCache({
        onError: (error) => {
          if (error instanceof ApiError && error.status === 401) {
            client.clear();
            router.replace("/login?expired=1");
          }
        },
      }),
      defaultOptions: {
        queries: {
          retry: shouldRetry,
          staleTime: 10_000,
          refetchOnWindowFocus: false,
        },
        mutations: {
          retry: shouldRetry,
        },
      },
    });

    return client;
  });

  return (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  );
}
