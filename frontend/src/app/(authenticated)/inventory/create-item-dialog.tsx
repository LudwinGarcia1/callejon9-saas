"use client";

import { useState } from "react";
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
  DialogTrigger,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { FieldError } from "@/components/shared/field-error";
import { ApiError, api } from "@/lib/api";
import { endpoints } from "@/lib/endpoints";
import { queryKeys } from "@/lib/query-keys";
import type { CreateInventoryItemRequest, InventoryItemResponse } from "@/lib/types";

/**
 * Alta de insumo. El stock inicial es opcional: si se captura, el backend
 * registra su movimiento IN "Stock inicial", de modo que ni el alta cambia el
 * stock sin dejar una fila que lo explique.
 */
export function CreateItemDialog() {
  const [open, setOpen] = useState(false);
  const queryClient = useQueryClient();

  const createMutation = useMutation({
    mutationFn: (payload: CreateInventoryItemRequest) =>
      api.post<InventoryItemResponse>(endpoints.inventory.createItem(), payload),
    onSuccess: (item) => {
      queryClient.invalidateQueries({ queryKey: queryKeys.inventory.items(true) });
      queryClient.invalidateQueries({ queryKey: ["inventory", "movements"] });
      toast.success(`Insumo "${item.name}" creado.`);
      setOpen(false);
    },
  });

  function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const formData = new FormData(event.currentTarget);

    createMutation.mutate({
      name: String(formData.get("name") ?? ""),
      unit: String(formData.get("unit") ?? ""),
      minStock: optionalNumber(formData.get("minStock")),
      unitCost: optionalNumber(formData.get("unitCost")),
      initialStock: optionalNumber(formData.get("initialStock")),
    });
  }

  const apiError = createMutation.error instanceof ApiError ? createMutation.error : null;
  const hasFieldErrors = Boolean(apiError?.errors && Object.keys(apiError.errors).length > 0);

  return (
    <Dialog
      open={open}
      onOpenChange={(nextOpen) => {
        setOpen(nextOpen);
        if (!nextOpen) {
          createMutation.reset();
        }
      }}
    >
      <DialogTrigger asChild>
        <Button>Nuevo insumo</Button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Nuevo insumo</DialogTitle>
          <DialogDescription>
            La unidad queda fija en cuanto el insumo tenga movimientos, asi que conviene elegirla
            con cuidado.
          </DialogDescription>
        </DialogHeader>
        <form onSubmit={handleSubmit} className="flex flex-col gap-4">
          {apiError && !hasFieldErrors && (
            <Alert variant="destructive">
              <AlertTitle>No se pudo crear el insumo</AlertTitle>
              <AlertDescription>{apiError.message}</AlertDescription>
            </Alert>
          )}

          <div className="flex flex-col gap-1.5">
            <Label htmlFor="name">Nombre</Label>
            <Input id="name" name="name" required maxLength={160} disabled={createMutation.isPending} />
            <FieldError error={apiError} field="name" />
          </div>

          <div className="flex flex-col gap-1.5">
            <Label htmlFor="unit">Unidad</Label>
            <Input
              id="unit"
              name="unit"
              required
              maxLength={20}
              placeholder="kg, litro, pieza"
              disabled={createMutation.isPending}
            />
            <FieldError error={apiError} field="unit" />
          </div>

          <div className="grid grid-cols-2 gap-4">
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="minStock">Minimo (opcional)</Label>
              <Input
                id="minStock"
                name="minStock"
                type="number"
                min={0}
                step="0.001"
                disabled={createMutation.isPending}
              />
              <FieldError error={apiError} field="minStock" />
            </div>

            <div className="flex flex-col gap-1.5">
              <Label htmlFor="unitCost">Costo unitario (opcional)</Label>
              <Input
                id="unitCost"
                name="unitCost"
                type="number"
                min={0}
                step="0.01"
                disabled={createMutation.isPending}
              />
              <FieldError error={apiError} field="unitCost" />
            </div>
          </div>

          <div className="flex flex-col gap-1.5">
            <Label htmlFor="initialStock">Stock inicial (opcional)</Label>
            <Input
              id="initialStock"
              name="initialStock"
              type="number"
              min={0}
              step="0.001"
              disabled={createMutation.isPending}
            />
            <p className="text-xs text-muted-foreground">
              Si lo capturas, queda registrado como una entrada en el historial.
            </p>
            <FieldError error={apiError} field="initialStock" />
          </div>

          <DialogFooter>
            <Button type="submit" disabled={createMutation.isPending}>
              {createMutation.isPending ? "Guardando..." : "Crear insumo"}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}

/** Un campo numerico vacio debe viajar como undefined, no como 0 ni NaN. */
function optionalNumber(value: FormDataEntryValue | null): number | undefined {
  const text = String(value ?? "").trim();
  return text === "" ? undefined : Number(text);
}
