"use client";

import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";

import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Label } from "@/components/ui/label";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { QueryState } from "@/components/shared/query-state";
import { ApiError, api } from "@/lib/api";
import { endpoints } from "@/lib/endpoints";
import { queryKeys } from "@/lib/query-keys";
import type { RestaurantSettingsResponse, UpdateRestaurantSettingsRequest } from "@/lib/types";

/**
 * Zonas de Mexico que se ofrecen en la interfaz. El backend acepta cualquier
 * ID IANA geografico; si el restaurante ya tiene otra, se agrega al final
 * para que el Select no quede vacio.
 */
export const MEXICO_TIME_ZONES: ReadonlyArray<{ id: string; label: string }> = [
  { id: "America/Mexico_City", label: "Centro — Ciudad de México, Guadalajara" },
  { id: "America/Monterrey", label: "Centro — Monterrey" },
  { id: "America/Merida", label: "Centro — Mérida" },
  { id: "America/Cancun", label: "Sureste — Cancún, Quintana Roo" },
  { id: "America/Chihuahua", label: "Chihuahua" },
  { id: "America/Mazatlan", label: "Pacífico — Mazatlán, La Paz" },
  { id: "America/Hermosillo", label: "Sonora — Hermosillo" },
  { id: "America/Tijuana", label: "Noroeste — Tijuana (con horario de verano)" },
  { id: "America/Ciudad_Juarez", label: "Ciudad Juárez (con horario de verano)" },
  { id: "America/Matamoros", label: "Matamoros (con horario de verano)" },
];

function currentTimeIn(timeZone: string): string {
  return new Intl.DateTimeFormat("es-MX", { timeZone, hour: "2-digit", minute: "2-digit" }).format(
    new Date(),
  );
}

/**
 * Zona horaria del restaurante (CAL-19). Define en que medianoche se corta
 * el dia del historial de ventas, la analitica y los movimientos de
 * inventario. Cambiarla no reescribe nada: el historial se reinterpreta.
 */
export function RestaurantSettingsSection() {
  const queryClient = useQueryClient();
  const settingsQuery = useQuery({
    queryKey: queryKeys.settings.detail(),
    queryFn: () => api.get<RestaurantSettingsResponse>(endpoints.settings.detail()),
  });
  // null = el ADMIN no ha tocado el Select; se muestra el valor guardado.
  const [draft, setDraft] = useState<string | null>(null);

  const saveMutation = useMutation({
    mutationFn: (body: UpdateRestaurantSettingsRequest) =>
      api.patch<RestaurantSettingsResponse>(endpoints.settings.update(), body),
    onSuccess: (settings) => {
      queryClient.setQueryData(queryKeys.settings.detail(), settings);
      setDraft(null);
      // La sesion lleva la zona, y los rangos por dia ya cachados quedaron
      // calculados con la anterior.
      queryClient.invalidateQueries({ queryKey: queryKeys.session.me() });
      queryClient.invalidateQueries({ queryKey: queryKeys.sales.all() });
      queryClient.invalidateQueries({ queryKey: queryKeys.analytics.all() });
      queryClient.invalidateQueries({ queryKey: queryKeys.inventory.allMovements() });
      toast.success("Zona horaria actualizada.");
    },
  });

  const saved = settingsQuery.data?.timezone;
  const selected = draft ?? saved ?? "";
  const options =
    saved && !MEXICO_TIME_ZONES.some((zone) => zone.id === saved)
      ? [...MEXICO_TIME_ZONES, { id: saved, label: saved }]
      : MEXICO_TIME_ZONES;
  const apiError = saveMutation.error instanceof ApiError ? saveMutation.error : null;

  return (
    <QueryState isLoading={settingsQuery.isLoading} error={settingsQuery.error}>
      <Card>
        <CardHeader>
          <CardTitle>Zona horaria</CardTitle>
          <CardDescription>
            El historial de ventas, la analítica y el inventario cortan el día a la medianoche
            de esta zona. Cambiarla no modifica ventas registradas: el historial se vuelve a
            agrupar con la zona nueva.
          </CardDescription>
        </CardHeader>
        <CardContent className="flex flex-col gap-4">
          {apiError && (
            <Alert variant="destructive">
              <AlertTitle>No se pudo guardar la zona horaria</AlertTitle>
              <AlertDescription>{apiError.errors?.timezone ?? apiError.message}</AlertDescription>
            </Alert>
          )}
          <div className="flex flex-col gap-2 sm:max-w-md">
            <Label htmlFor="timezone">Zona del restaurante</Label>
            <Select value={selected} onValueChange={setDraft}>
              <SelectTrigger id="timezone" className="w-full">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {options.map((zone) => (
                  <SelectItem key={zone.id} value={zone.id}>
                    {zone.label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
            {selected && (
              <p className="text-sm text-muted-foreground">
                Hora actual en esta zona:{" "}
                <span className="font-mono tabular-nums">{currentTimeIn(selected)}</span>
              </p>
            )}
          </div>
          <div>
            <Button
              type="button"
              onClick={() => saveMutation.mutate({ timezone: selected })}
              disabled={!draft || draft === saved || saveMutation.isPending}
            >
              {saveMutation.isPending ? "Guardando…" : "Guardar zona horaria"}
            </Button>
          </div>
        </CardContent>
      </Card>
    </QueryState>
  );
}
