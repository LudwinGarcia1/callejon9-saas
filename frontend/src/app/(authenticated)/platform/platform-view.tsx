"use client";

import { useQuery } from "@tanstack/react-query";

import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { QueryState } from "@/components/shared/query-state";
import { Money } from "@/components/shared/money";
import { useSession } from "@/hooks/use-session";
import { api } from "@/lib/api";
import { endpoints } from "@/lib/endpoints";
import { queryKeys } from "@/lib/query-keys";
import type { Plan } from "@/lib/types";

/**
 * Panel de super administrador: los planes disponibles en la plataforma.
 *
 * GET /api/v1/platform/plans exige rol SUPER_ADMIN (SecurityConfig lo marca
 * con hasRole en toda la ruta /api/v1/platform/**); cualquier otro rol
 * recibe 403. Como providers.tsx deliberadamente no redirige en un 403 (para
 * no generar un ciclo con /login), esta pantalla debe mostrar su propio
 * estado honesto en vez de dejar que se vea como un error roto — por eso ni
 * siquiera se dispara la consulta si la sesion ya resuelta no es de super
 * administrador.
 */
export function PlatformView() {
  const { user, isLoading: isSessionLoading } = useSession();
  const isSuperAdmin = user?.role === "SUPER_ADMIN";

  const plansQuery = useQuery({
    queryKey: queryKeys.platform.plans(),
    queryFn: () => api.get<Plan[]>(endpoints.platform.plans()),
    enabled: isSuperAdmin,
  });

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-xl font-semibold">Panel de plataforma</h1>
        <p className="text-sm text-muted-foreground">
          Planes disponibles para los restaurantes de Callejon 9.
        </p>
      </div>

      {isSessionLoading ? (
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
          {Array.from({ length: 3 }).map((_, index) => (
            <Skeleton key={index} className="h-40 w-full" />
          ))}
        </div>
      ) : !isSuperAdmin ? (
        <Alert>
          <AlertTitle>Sin permiso</AlertTitle>
          <AlertDescription>
            Esta seccion requiere una cuenta de super administrador.
          </AlertDescription>
        </Alert>
      ) : (
        <QueryState
          isLoading={plansQuery.isLoading}
          error={plansQuery.error}
          isEmpty={plansQuery.data?.length === 0}
          emptyMessage="Todavia no hay planes registrados."
        >
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {plansQuery.data?.map((plan) => (
              <Card key={plan.code}>
                <CardHeader>
                  <CardDescription>{plan.code}</CardDescription>
                  <CardTitle>{plan.name}</CardTitle>
                </CardHeader>
                <CardContent className="flex flex-col gap-2">
                  <p className="text-2xl font-semibold">
                    <Money amount={plan.priceMonthly} />
                    <span className="text-sm font-normal text-muted-foreground"> / mes</span>
                  </p>
                  <p className="text-sm text-muted-foreground">
                    Hasta {plan.maxUsers} usuarios
                  </p>
                  <p className="text-sm text-muted-foreground">
                    Hasta {plan.maxTables} mesas
                  </p>
                </CardContent>
              </Card>
            ))}
          </div>
        </QueryState>
      )}
    </div>
  );
}
