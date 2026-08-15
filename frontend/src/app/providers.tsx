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
 * Un 4xx es determinista: el servidor ya evaluo la peticion y repetirla tal
 * cual produce exactamente la misma respuesta. Reintentarlo no arregla nada y
 * si retrasa el mensaje de error -- un 409 de regla de negocio ("ya tiene
 * movimientos en kg") tardaba un segundo extra en verse, y con la pestana en
 * segundo plano se quedaba esperando indefinidamente, porque React Query pausa
 * los reintentos mientras el documento no es visible.
 *
 * Las dos excepciones son 408 y 429: dependen del momento, no del contenido de
 * la peticion, asi que ahi un reintento si tiene sentido. Lo demas -- fallo de
 * red, 5xx -- se reintenta una vez, que basta en una red estable.
 */
function shouldRetry(failureCount: number, error: unknown): boolean {
  if (error instanceof ApiError && error.status >= 400 && error.status < 500) {
    return (error.status === 408 || error.status === 429) && failureCount < 1;
  }
  return failureCount < 1;
}

/** Provee un unico QueryClient por sesion de navegador a todo el arbol. */
export function Providers({ children }: { children: React.ReactNode }) {
  const router = useRouter();

  const [queryClient] = useState(() => {
    /**
     * Se arma una sola vez por sesion de navegador, asi que esta bandera vive
     * exactamente lo mismo que el cliente al que protege.
     *
     * Sin ella, manejar el 401 se realimenta: `clear()` vacia el cache, los
     * observers montados se quedan sin datos y vuelven a pedir en el acto, esa
     * respuesta es otro 401 y entra al mismo manejador. Medido en la pantalla
     * de inventario: 500 peticiones en seis segundos, y la redireccion a login
     * sin llegar a ocurrir porque el bucle no dejaba avanzar la navegacion.
     */
    let sessionExpired = false;

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
     *
     * El cache se limpia igual que antes: en multi-tenant, dejar ahi los datos
     * del usuario anterior es lo que no puede pasar. Solo se hace una vez.
     */
    function onRequestFailed(error: unknown) {
      if (!(error instanceof ApiError) || error.status !== 401 || sessionExpired) {
        return;
      }

      sessionExpired = true;
      client.clear();
      router.replace("/login?expired=1");
    }

    /** Una peticion que vuelve a funcionar significa que hay sesion de nuevo. */
    function onRequestSucceeded() {
      sessionExpired = false;
    }

    const client: QueryClient = new QueryClient({
      queryCache: new QueryCache({
        onSuccess: onRequestSucceeded,
        onError: onRequestFailed,
      }),
      mutationCache: new MutationCache({
        onSuccess: onRequestSucceeded,
        onError: onRequestFailed,
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
