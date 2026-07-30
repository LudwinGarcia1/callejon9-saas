"use client";

import { useRouter } from "next/navigation";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";

import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { ApiError, api } from "@/lib/api";
import { endpoints } from "@/lib/endpoints";
import { queryKeys } from "@/lib/query-keys";
import type { OpenOrderRequest, OrderResponse, TableResponse } from "@/lib/types";

interface OpenTableDialogProps {
  /** Mesa libre seleccionada, o null si el dialogo esta cerrado. */
  table: TableResponse | null;
  onOpenChange: (open: boolean) => void;
}

/**
 * Dialogo para abrir una orden nueva en una mesa libre. Dos meseros pueden
 * tocar la misma mesa casi al mismo tiempo: el backend bloquea la fila y solo
 * uno gana, el otro recibe un 409 con un detalle en espanol que se muestra
 * aqui mismo en vez de dejar que se vea como un error generico.
 */
export function OpenTableDialog({ table, onOpenChange }: OpenTableDialogProps) {
  const router = useRouter();
  const queryClient = useQueryClient();

  const openOrderMutation = useMutation({
    mutationFn: (payload: OpenOrderRequest) =>
      api.post<OrderResponse>(endpoints.orders.open(), payload),
    onSuccess: (order) => {
      queryClient.invalidateQueries({ queryKey: queryKeys.tables.all() });
      queryClient.invalidateQueries({ queryKey: queryKeys.orders.all() });
      toast.success(`Mesa ${table?.number} ocupada. Orden ${order.folio} abierta.`);
      onOpenChange(false);
      router.push(`/waiter/order/${order.id}`);
    },
    onError: (error) => {
      const message =
        error instanceof ApiError ? error.message : "No se pudo abrir la orden.";
      toast.error(message);
    },
  });

  function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!table) {
      return;
    }

    const formData = new FormData(event.currentTarget);
    openOrderMutation.mutate({
      tableId: table.id,
      guestCount: Number(formData.get("guestCount")),
    });
  }

  const apiError = openOrderMutation.error instanceof ApiError ? openOrderMutation.error : null;

  return (
    <Dialog
      open={table !== null}
      onOpenChange={(nextOpen) => {
        onOpenChange(nextOpen);
        if (!nextOpen) {
          openOrderMutation.reset();
        }
      }}
    >
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Abrir mesa {table?.number}</DialogTitle>
          <DialogDescription>
            Indica cuantos comensales se sentaron para abrir la orden.
          </DialogDescription>
        </DialogHeader>
        <form onSubmit={handleSubmit} className="flex flex-col gap-4">
          {apiError && (
            <Alert variant="destructive">
              <AlertTitle>No se pudo abrir la mesa</AlertTitle>
              <AlertDescription>{apiError.message}</AlertDescription>
            </Alert>
          )}

          <div className="flex flex-col gap-1.5">
            <Label htmlFor="guestCount">Numero de comensales</Label>
            <Input
              id="guestCount"
              name="guestCount"
              type="number"
              min={1}
              defaultValue={2}
              required
              autoFocus
              disabled={openOrderMutation.isPending}
            />
          </div>

          <DialogFooter>
            <Button type="submit" disabled={openOrderMutation.isPending}>
              {openOrderMutation.isPending ? "Abriendo..." : "Abrir orden"}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
