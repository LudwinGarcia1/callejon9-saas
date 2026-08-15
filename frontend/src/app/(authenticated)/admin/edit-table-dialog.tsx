"use client";

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
import { FieldError } from "@/components/shared/field-error";
import { ApiError, api } from "@/lib/api";
import { endpoints } from "@/lib/endpoints";
import { queryKeys } from "@/lib/query-keys";
import type { TableResponse, UpdateTableRequest } from "@/lib/types";

interface EditTableDialogProps {
  /** Mesa a editar, o null si el dialogo esta cerrado. */
  table: TableResponse | null;
  onOpenChange: (open: boolean) => void;
}

/**
 * Dialogo para corregir el numero o la capacidad de una mesa ya existente.
 * Un numero repetido llega como 409 generico (violacion de restriccion
 * UNIQUE), igual que en el alta.
 */
export function EditTableDialog({ table, onOpenChange }: EditTableDialogProps) {
  const queryClient = useQueryClient();

  const updateMutation = useMutation({
    mutationFn: (payload: UpdateTableRequest) => {
      if (!table) {
        throw new Error("No hay mesa seleccionada.");
      }
      return api.put<TableResponse>(endpoints.tables.update(table.id), payload);
    },
    onSuccess: (updated) => {
      queryClient.invalidateQueries({ queryKey: queryKeys.tables.all(true) });
      toast.success(`Mesa ${updated.number} actualizada.`);
      onOpenChange(false);
    },
    onError: (error) => {
      const message =
        error instanceof ApiError ? error.message : "No se pudo actualizar la mesa.";
      toast.error(message);
    },
  });

  function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const formData = new FormData(event.currentTarget);

    updateMutation.mutate({
      number: Number(formData.get("number")),
      capacity: Number(formData.get("capacity")),
    });
  }

  const apiError = updateMutation.error instanceof ApiError ? updateMutation.error : null;
  const hasFieldErrors = Boolean(apiError?.errors && Object.keys(apiError.errors).length > 0);

  return (
    <Dialog
      open={table !== null}
      onOpenChange={(nextOpen) => {
        onOpenChange(nextOpen);
        if (!nextOpen) {
          updateMutation.reset();
        }
      }}
    >
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Editar mesa</DialogTitle>
          <DialogDescription>Corrige el número o la capacidad de la mesa.</DialogDescription>
        </DialogHeader>
        {table && (
          <form onSubmit={handleSubmit} className="flex flex-col gap-4">
            {apiError && !hasFieldErrors && (
              <Alert variant="destructive">
                <AlertTitle>No se pudo actualizar la mesa</AlertTitle>
                <AlertDescription>{apiError.message}</AlertDescription>
              </Alert>
            )}

            <div className="flex flex-col gap-1.5">
              <Label htmlFor="edit-table-number">Número</Label>
              <Input
                id="edit-table-number"
                name="number"
                type="number"
                min={1}
                required
                defaultValue={table.number}
                disabled={updateMutation.isPending}
              />
              <FieldError error={apiError} field="number" />
            </div>

            <div className="flex flex-col gap-1.5">
              <Label htmlFor="edit-table-capacity">Capacidad</Label>
              <Input
                id="edit-table-capacity"
                name="capacity"
                type="number"
                min={1}
                required
                defaultValue={table.capacity}
                disabled={updateMutation.isPending}
              />
              <FieldError error={apiError} field="capacity" />
            </div>

            <DialogFooter>
              <Button type="submit" size="lg" disabled={updateMutation.isPending}>
                {updateMutation.isPending ? "Guardando…" : "Guardar cambios"}
              </Button>
            </DialogFooter>
          </form>
        )}
      </DialogContent>
    </Dialog>
  );
}
