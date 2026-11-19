"use client";

import {
  MutationCache,
  QueryCache,
  QueryClient,
  QueryClientProvider,
} from "@tanstack/react-query";
import { usePathname, useRouter } from "next/navigation";
import { useEffect, useRef, useState } from "react";

import { ApiError } from "@/lib/api";
import { isProtectedPagePath, loginPathFor } from "@/lib/protected-pages";

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

/**
 * Estado del manejo de sesion. Vive lo mismo que el QueryClient al que
 * acompana: uno por sesion de navegador.
 */
interface SessionTracking {
  /**
   * Ya se pidio la redireccion a login. Sin esta bandera, manejar el 401 se
   * realimenta: `clear()` vacia el cache, los observers montados se quedan sin
   * datos y vuelven a pedir en el acto, esa respuesta es otro 401 y entra al
   * mismo manejador. Medido en la pantalla de inventario: 500 peticiones en
   * seis segundos, y la redireccion sin llegar a ocurrir.
   */
  redirecting: boolean;
  /** Hubo una peticion con sesion: un 401 a partir de aqui es sesion vencida. */
  hadSession: boolean;
}

function currentPath(): string {
  return typeof window === "undefined" ? "" : window.location.pathname;
}

/** Provee un unico QueryClient por sesion de navegador a todo el arbol. */
export function Providers({ children }: { children: React.ReactNode }) {
  const router = useRouter();
  const pathname = usePathname();
  const tracking = useRef<SessionTracking>({ redirecting: false, hadSession: false });

  const [queryClient] = useState(() => {
    /**
     * Punto unico de manejo de perdida de sesion: cualquier query o
     * mutation que falle con 401 en una pantalla con sesion significa que la
     * cookie `access_token` expiro o ya no es valida, asi que limpiamos todo el
     * cache y redirigimos a login. Un 403 es distinto — significa rol
     * equivocado o falta de contexto de tenant (`NoTenantContextException`,
     * que el backend mapea a 403) y NO debe redirigir a login: eso crearia un
     * ciclo de "inicia sesion correctamente -> rebota de inmediato". Ese caso
     * lo debe renderizar la pantalla que hizo la peticion, como un estado de
     * "sin permiso".
     *
     * Fuera de las pantallas con sesion (login, alta, menu publico) un 401 es
     * una respuesta esperada, como una contrasena equivocada, y no se toca:
     * antes llevaba a /login?expired=1 y el login mostraba "Sesion expirada"
     * a quien nunca habia entrado.
     *
     * El cache se limpia: en multi-tenant, dejar ahi los datos del usuario
     * anterior es lo que no puede pasar.
     */
    function onRequestFailed(error: unknown) {
      if (!(error instanceof ApiError) || error.status !== 401) {
        return;
      }
      const path = currentPath();
      if (!isProtectedPagePath(path) || tracking.current.redirecting) {
        return;
      }

      tracking.current.redirecting = true;
      // Sin una peticion previa con sesion no hubo nada que expirara: es una
      // pantalla protegida abierta sin sesion, por ejemplo con Atras despues de
      // cerrar sesion (CAL-194). Se vuelve al login sin el aviso de expiracion.
      const destination = tracking.current.hadSession ? "/login?expired=1" : loginPathFor(path);
      tracking.current.hadSession = false;
      client.clear();
      router.replace(destination);
    }

    /** Una peticion que vuelve a funcionar significa que hay sesion de nuevo. */
    function onRequestSucceeded() {
      tracking.current.redirecting = false;
      if (isProtectedPagePath(currentPath())) {
        tracking.current.hadSession = true;
      }
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

  /**
   * Al salir de las pantallas con sesion --cerrar sesion o la redireccion por
   * un 401-- se limpia el cache y se rearma el manejo del 401 (CAL-194).
   *
   * Limpiar aqui, ya fuera de la ruta protegida, y no al pulsar "Cerrar
   * sesion": en ese momento sus consultas siguen montadas, vuelven a pedir con
   * el cache vacio y su 401 se tomaba por una sesion expirada. Y rearmar al
   * llegar al login evita que la bandera quede puesta: antes, al volver con
   * Atras a una pantalla protegida, su 401 se ignoraba y se veia un error.
   */
  const previousPath = useRef(pathname);
  useEffect(() => {
    const leftProtectedArea =
      isProtectedPagePath(previousPath.current) && !isProtectedPagePath(pathname);
    previousPath.current = pathname;
    if (isProtectedPagePath(pathname)) {
      return;
    }
    tracking.current.redirecting = false;
    // Solo al salir: las pantallas publicas (menu QR, alta) usan su propio
    // cache y limpiarlo al entrar repetiria sus consultas.
    if (leftProtectedArea) {
      tracking.current.hadSession = false;
      queryClient.clear();
    }
  }, [pathname, queryClient]);

  return (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  );
}
