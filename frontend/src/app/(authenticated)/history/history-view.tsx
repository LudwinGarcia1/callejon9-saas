"use client";

import { useState } from "react";
import { useMutation, useQuery } from "@tanstack/react-query";
import { toast } from "sonner";

import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { ScreenMetric, ScreenShell } from "@/components/layout/screen-shell";
import { Money } from "@/components/shared/money";
import { QueryState } from "@/components/shared/query-state";
import { StatusBadge } from "@/components/shared/status-badge";
import { TicketSummary } from "@/components/shared/ticket-summary";
import { ApiError, api } from "@/lib/api";
import { endpoints } from "@/lib/endpoints";
import { formatShortDate, formatShortTime, todayIsoDate } from "@/lib/format";
import { queryKeys } from "@/lib/query-keys";
import { cn } from "@/lib/utils";
import type { SaleHistoryRow, SalesHistoryResponse, TicketResponse } from "@/lib/types";

/**
 * Pantalla de historial de ventas: una vez cobrada, una orden desaparece de
 * mesero/cocina/caja, pero debe seguir siendo consultable aqui. Muestra el
 * rango solicitado (hoy por defecto) con su resumen, y permite localizar un
 * ticket puntual por folio sin importar si esta dentro del rango visible.
 */
export function HistoryView() {
  const today = todayIsoDate();
  const [range, setRange] = useState({ from: today, to: today });
  const [selectedSaleId, setSelectedSaleId] = useState<string | null>(null);
  const [folioResult, setFolioResult] = useState<TicketResponse | null>(null);

  const salesQuery = useQuery({
    queryKey: queryKeys.sales.history(range.from, range.to),
    queryFn: () =>
      api.get<SalesHistoryResponse>(endpoints.sales.history(), {
        from: range.from,
        to: range.to,
      }),
  });

  const folioMutation = useMutation({
    mutationFn: (folio: string) => api.get<TicketResponse>(endpoints.tickets.byFolio(), { folio }),
    onSuccess: (ticket) => {
      setFolioResult(ticket);
      setSelectedSaleId(null);
    },
    onError: (error) => {
      toast.error(
        error instanceof ApiError && error.status === 404
          ? "No existe ningún ticket con ese folio."
          : "No se pudo buscar el ticket.",
      );
    },
  });

  const sales = salesQuery.data?.sales ?? [];
  const summary = salesQuery.data?.summary;

  function handleRangeSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const formData = new FormData(event.currentTarget);
    const from = String(formData.get("from") || today);
    const to = String(formData.get("to") || today);
    setRange({ from, to });
  }

  function handleFolioSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const formData = new FormData(event.currentTarget);
    const folio = String(formData.get("folio") ?? "").trim();
    if (!folio) {
      return;
    }
    folioMutation.mutate(folio);
  }

  function handleSelectSale(saleId: string) {
    setSelectedSaleId(saleId);
    setFolioResult(null);
  }

  return (
    <ScreenShell
      title="Historial de ventas"
      subtitle="Consulta lo que se ha cobrado en un rango de fechas o encuentra un ticket por folio."
      actions={
        <>
          <ScreenMetric label="Ventas en el rango" value={summary?.count ?? 0} />
          <ScreenMetric label="Total cobrado" value={<Money amount={summary?.total ?? 0} />} accent />
        </>
      }
      contentClassName="flex flex-col gap-6"
    >
      <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
        <Card>
          <CardHeader>
            <CardTitle>Rango de fechas</CardTitle>
          </CardHeader>
          <CardContent>
            <form onSubmit={handleRangeSubmit} className="flex flex-wrap items-end gap-3">
              <div className="flex flex-col gap-1.5">
                <Label htmlFor="from">Desde</Label>
                <Input id="from" name="from" type="date" defaultValue={today} required />
              </div>
              <div className="flex flex-col gap-1.5">
                <Label htmlFor="to">Hasta</Label>
                <Input id="to" name="to" type="date" defaultValue={today} required />
              </div>
              <Button type="submit">Buscar</Button>
            </form>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>Buscar ticket por folio</CardTitle>
          </CardHeader>
          <CardContent>
            <form onSubmit={handleFolioSubmit} className="flex flex-wrap items-end gap-3">
              <div className="flex flex-1 flex-col gap-1.5">
                <Label htmlFor="folio">Folio</Label>
                <Input id="folio" name="folio" placeholder="TCK-260731225310" />
              </div>
              <Button type="submit" disabled={folioMutation.isPending}>
                {folioMutation.isPending ? "Buscando…" : "Buscar"}
              </Button>
            </form>
          </CardContent>
        </Card>
      </div>

      <div className="grid grid-cols-1 gap-6 lg:grid-cols-[minmax(0,1.4fr)_minmax(0,1fr)]">
        <Card>
          <CardHeader>
            <CardTitle>Ventas</CardTitle>
          </CardHeader>
          <CardContent>
            <QueryState
              isLoading={salesQuery.isLoading}
              error={salesQuery.error}
              isEmpty={sales.length === 0}
              emptyMessage="No hay ventas en el rango seleccionado."
            >
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Orden</TableHead>
                    <TableHead>Mesa</TableHead>
                    <TableHead>Cajero</TableHead>
                    <TableHead>Pago</TableHead>
                    <TableHead>Total</TableHead>
                    <TableHead>Fecha</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {sales.map((sale) => (
                    <TableRow
                      key={sale.id}
                      onClick={() => handleSelectSale(sale.id)}
                      className={cn(
                        "cursor-pointer",
                        selectedSaleId === sale.id && "bg-accent",
                      )}
                    >
                      <TableCell className="font-mono text-[13px]">{sale.orderFolio}</TableCell>
                      <TableCell>
                        {sale.tableNumber !== null ? `Mesa ${sale.tableNumber}` : "Para llevar"}
                      </TableCell>
                      <TableCell>{sale.cashierName ?? "—"}</TableCell>
                      <TableCell>
                        <StatusBadge kind="payment" status={sale.paymentMethod} />
                      </TableCell>
                      <TableCell>
                        <Money amount={sale.total} />
                      </TableCell>
                      <TableCell className="font-mono text-[13px] text-muted-foreground">
                        {formatShortDate(sale.createdAt)} {formatShortTime(sale.createdAt)}
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </QueryState>
          </CardContent>
        </Card>

        <SaleTicketPanel saleId={selectedSaleId} sales={sales} folioResult={folioResult} />
      </div>
    </ScreenShell>
  );
}

interface SaleTicketPanelProps {
  saleId: string | null;
  sales: SaleHistoryRow[];
  folioResult: TicketResponse | null;
}

/**
 * Detalle de la venta seleccionada: busca su ticket por id (la fila del
 * historial solo trae el id, no el detalle completo) y lo muestra con
 * TicketSummary, el mismo componente que usa caja justo despues de cobrar.
 * Si en vez de eso hay un resultado de busqueda por folio, ese tiene
 * prioridad -- son dos formas independientes de llegar a un ticket.
 */
function SaleTicketPanel({ saleId, sales, folioResult }: SaleTicketPanelProps) {
  const selectedSale = sales.find((sale) => sale.id === saleId) ?? null;
  const ticketId = selectedSale?.ticketId ?? null;

  const ticketQuery = useQuery({
    queryKey: queryKeys.tickets.detail(ticketId ?? ""),
    queryFn: () => api.get<TicketResponse>(endpoints.tickets.detail(ticketId as string)),
    enabled: Boolean(ticketId) && !folioResult,
  });

  if (folioResult) {
    return (
      <Card>
        <CardHeader>
          <CardTitle>Ticket encontrado</CardTitle>
        </CardHeader>
        <CardContent>
          <TicketSummary ticket={folioResult} />
        </CardContent>
      </Card>
    );
  }

  if (!saleId) {
    return (
      <Card>
        <CardContent className="flex h-full items-center justify-center py-12">
          <p className="text-sm text-muted-foreground">
            Selecciona una venta de la lista para ver su ticket.
          </p>
        </CardContent>
      </Card>
    );
  }

  if (!ticketId) {
    return (
      <Card>
        <CardContent className="flex h-full items-center justify-center py-12">
          <p className="text-sm text-muted-foreground">Esta venta no tiene un ticket asociado.</p>
        </CardContent>
      </Card>
    );
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>Ticket</CardTitle>
      </CardHeader>
      <CardContent>
        <QueryState
          isLoading={ticketQuery.isLoading}
          error={ticketQuery.error}
          isEmpty={!ticketQuery.data}
          emptyMessage="No se encontró el ticket."
        >
          {ticketQuery.data && <TicketSummary ticket={ticketQuery.data} />}
        </QueryState>
      </CardContent>
    </Card>
  );
}
