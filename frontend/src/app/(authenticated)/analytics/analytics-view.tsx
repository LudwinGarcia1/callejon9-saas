"use client";

import { useEffect, useState, type FormEvent, type ReactNode } from "react";
import { useQuery } from "@tanstack/react-query";

import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { QueryState } from "@/components/shared/query-state";
import { DailySalesChart, DailySalesTable } from "@/components/charts/daily-sales-chart";
import { ParetoChart, ParetoTable } from "@/components/charts/pareto-chart";
import { PaymentMixChart, PaymentMixTable } from "@/components/charts/payment-mix-chart";
import { api } from "@/lib/api";
import { endpoints } from "@/lib/endpoints";
import { queryKeys } from "@/lib/query-keys";
import type { AnalyticsResponse } from "@/lib/types";
import styles from "./analytics.module.css";

/**
 * Pantalla de analitica: Pareto de productos, ventas por dia y mezcla de
 * pago, reconectando la vista con la metodologia CRISP-DM del proyecto
 * original.
 *
 * El rango inicial NO se calcula en el cliente: `from`/`to` viajan vacios en
 * la primera consulta para que el backend aplique su propio default (los
 * ultimos 7 dias terminando hoy, resuelto en la zona horaria del negocio).
 * Calcularlo aqui con `Date` del navegador repetiria justo el error de zona
 * horaria que este modulo corrige del lado del servidor. Una vez llega la
 * respuesta, los campos de fecha se precargan con el rango real que el
 * servidor uso (el primer y el ultimo dia de `salesByDay`).
 */
export function AnalyticsView() {
  const [range, setRange] = useState<{ from?: string; to?: string }>({});
  const [displayRange, setDisplayRange] = useState<{ from: string; to: string } | null>(null);

  const analyticsQuery = useQuery({
    queryKey: queryKeys.analytics.summary(range.from ?? "", range.to ?? ""),
    queryFn: () =>
      api.get<AnalyticsResponse>(endpoints.analytics.summary(), {
        from: range.from,
        to: range.to,
      }),
  });

  useEffect(() => {
    const days = analyticsQuery.data?.salesByDay;
    if (displayRange === null && days && days.length > 0) {
      setDisplayRange({ from: days[0].day, to: days[days.length - 1].day });
    }
  }, [analyticsQuery.data, displayRange]);

  function handleRangeSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const formData = new FormData(event.currentTarget);
    const from = String(formData.get("from") || "");
    const to = String(formData.get("to") || "");
    setRange({ from: from || undefined, to: to || undefined });
    setDisplayRange({ from, to });
  }

  const analytics = analyticsQuery.data;

  return (
    <div className={`flex flex-col gap-6 ${styles.scope}`}>
      <div>
        <h1 className="text-xl font-semibold">Analitica</h1>
        <p className="text-sm text-muted-foreground">
          Pareto de productos, ventas por dia y mezcla de metodos de pago del rango seleccionado.
        </p>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>Rango de fechas</CardTitle>
          <CardDescription>Por defecto, los ultimos 7 dias terminando hoy.</CardDescription>
        </CardHeader>
        <CardContent>
          <form onSubmit={handleRangeSubmit} className="flex flex-wrap items-end gap-3">
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="from">Desde</Label>
              <Input
                id="from"
                name="from"
                type="date"
                key={`from-${displayRange?.from ?? "default"}`}
                defaultValue={displayRange?.from}
              />
            </div>
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="to">Hasta</Label>
              <Input
                id="to"
                name="to"
                type="date"
                key={`to-${displayRange?.to ?? "default"}`}
                defaultValue={displayRange?.to}
              />
            </div>
            <Button type="submit">Buscar</Button>
          </form>
        </CardContent>
      </Card>

      <QueryState isLoading={analyticsQuery.isLoading} error={analyticsQuery.error}>
        {analytics && (
          <div className="flex flex-col gap-6">
            <ChartCard
              title="Pareto de productos"
              description="Participacion de ingresos por producto y su acumulado, con referencia en 80%."
            >
              {(view) =>
                view === "chart" ? (
                  <ParetoChart data={analytics.pareto} />
                ) : (
                  <ParetoTable data={analytics.pareto} />
                )
              }
            </ChartCard>

            <ChartCard title="Ventas por dia" description="Total cobrado cada dia del rango.">
              {(view) =>
                view === "chart" ? (
                  <DailySalesChart data={analytics.salesByDay} />
                ) : (
                  <DailySalesTable data={analytics.salesByDay} />
                )
              }
            </ChartCard>

            <ChartCard title="Metodos de pago" description="Ventas e ingreso por metodo de pago.">
              {(view) =>
                view === "chart" ? (
                  <PaymentMixChart data={analytics.paymentMix} />
                ) : (
                  <PaymentMixTable data={analytics.paymentMix} />
                )
              }
            </ChartCard>
          </div>
        )}
      </QueryState>
    </div>
  );
}

interface ChartCardProps {
  title: string;
  description: string;
  children: (view: "chart" | "table") => ReactNode;
}

/** Tarjeta comun a las tres graficas: titulo, descripcion y un alternador grafica/tabla. */
function ChartCard({ title, description, children }: ChartCardProps) {
  return (
    <Card>
      <CardHeader>
        <CardTitle>{title}</CardTitle>
        <CardDescription>{description}</CardDescription>
      </CardHeader>
      <CardContent>
        <Tabs defaultValue="chart">
          <TabsList>
            <TabsTrigger value="chart">Grafica</TabsTrigger>
            <TabsTrigger value="table">Tabla</TabsTrigger>
          </TabsList>
          <TabsContent value="chart">{children("chart")}</TabsContent>
          <TabsContent value="table">{children("table")}</TabsContent>
        </Tabs>
      </CardContent>
    </Card>
  );
}
