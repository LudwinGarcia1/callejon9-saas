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
import type { InventoryItemResponse, UpdateInventoryItemRequest } from "@/lib/types";

interface EditItemDialogProps {
  item: InventoryItemResponse | null;
  onOpenChange: (open: boolean) => void;
}

/**
 * Correccion de un insumo. No incluye el stock a proposito: el stock solo se
 * mueve con un movimiento. Si el insumo ya tiene historial, el backend rechaza
 * el cambio de unidad con 409 y el mensaje se muestra tal cual.
 */
export function EditItemDialog({ item, onOpenChange }: EditItemDialogProps) {
  const queryClient = useQueryClient();

  const updateMutation = useMutation({
    mutationFn: (payload: UpdateInventoryItemRequest) =>
      api.put<InventoryItemResponse>(endpoints.inventory.updateItem(item!.id), payload),
    onSuccess: (updated) => {
      queryClient.invalidateQueries({ queryKey: queryKeys.inventory.items(true) });
      queryClient.invalidateQueries({ queryKey: ["inventory", "movements"] });
      toast.success(`Insumo "${updated.name}" actualizado.`);
      onOpenChange(false);
    },
  });

  function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const formData = new FormData(event.currentTarget);

    updateMutation.mutate({
      name: String(formData.get("name") ?? ""),
      unit: String(formData.get("unit") ?? ""),
      minStock: optionalNumber(formData.get("minStock")),
      unitCost: optionalNumber(formData.get("unitCost")),
    });
  }

  const apiError = updateMutation.error instanceof ApiError ? updateMutation.error : null;
  const hasFieldErrors = Boolean(apiError?.errors && Object.keys(apiError.errors).length > 0);

  return (
    <Dialog
      open={item !== null}
      onOpenChange={(nextOpen) => {
        if (!nextOpen) {
          updateMutation.reset();
        }
        onOpenChange(nextOpen);
      }}
    >
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Editar insumo</DialogTitle>
          <DialogDescription>
            El stock no se edita aqui: se cambia registrando un movimiento.
          </DialogDescription>
        </DialogHeader>
        {item && (
          <form onSubmit={handleSubmit} className="flex flex-col gap-4">
            {apiError && !hasFieldErrors && (
              <Alert variant="destructive">
                <AlertTitle>No se pudo actualizar el insumo</AlertTitle>
                <AlertDescription>{apiError.message}</AlertDescription>
              </Alert>
            )}

            <div className="flex flex-col gap-1.5">
              <Label htmlFor="edit-name">Nombre</Label>
              <Input
                id="edit-name"
                name="name"
                required
                maxLength={160}
                defaultValue={item.name}
                disabled={updateMutation.isPending}
              />
              <FieldError error={apiError} field="name" />
            </div>

            <div className="flex flex-col gap-1.5">
              <Label htmlFor="edit-unit">Unidad</Label>
              <Input
                id="edit-unit"
                name="unit"
                required
                maxLength={20}
                defaultValue={item.unit}
                disabled={updateMutation.isPending}
              />
              <FieldError error={apiError} field="unit" />
            </div>

            <div className="grid grid-cols-2 gap-4">
              <div className="flex flex-col gap-1.5">
                <Label htmlFor="edit-minStock">Minimo</Label>
                <Input
                  id="edit-minStock"
                  name="minStock"
                  type="number"
                  min={0}
                  step="0.001"
                  defaultValue={item.minStock}
                  disabled={updateMutation.isPending}
                />
                <FieldError error={apiError} field="minStock" />
              </div>

              <div className="flex flex-col gap-1.5">
                <Label htmlFor="edit-unitCost">Costo unitario</Label>
                <Input
                  id="edit-unitCost"
                  name="unitCost"
                  type="number"
                  min={0}
                  step="0.01"
                  defaultValue={item.unitCost}
                  disabled={updateMutation.isPending}
                />
                <FieldError error={apiError} field="unitCost" />
              </div>
            </div>

            <DialogFooter>
              <Button type="submit" disabled={updateMutation.isPending}>
                {updateMutation.isPending ? "Guardando..." : "Guardar cambios"}
              </Button>
            </DialogFooter>
          </form>
        )}
      </DialogContent>
    </Dialog>
  );
}

/** Un campo numerico vacio debe viajar como undefined, no como 0 ni NaN. */
function optionalNumber(value: FormDataEntryValue | null): number | undefined {
  const text = String(value ?? "").trim();
  return text === "" ? undefined : Number(text);
}
