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
import type { CategoryResponse, UpdateCategoryRequest } from "@/lib/types";

interface EditCategoryDialogProps {
  /** Categoria a editar, o null si el dialogo esta cerrado. */
  category: CategoryResponse | null;
  onOpenChange: (open: boolean) => void;
}

/** Dialogo para corregir el nombre o el orden de una categoria ya existente.
 * Un nombre repetido llega como 409 con el detalle "Ya existe una categoria
 * llamada '...'." — GET /categories no filtra por estado, asi que aqui si
 * conviene invalidar la lista tras guardar. */
export function EditCategoryDialog({ category, onOpenChange }: EditCategoryDialogProps) {
  const queryClient = useQueryClient();

  const updateMutation = useMutation({
    mutationFn: (payload: UpdateCategoryRequest) => {
      if (!category) {
        throw new Error("No hay categoria seleccionada.");
      }
      return api.put<CategoryResponse>(endpoints.categories.update(category.id), payload);
    },
    onSuccess: (updated) => {
      queryClient.invalidateQueries({ queryKey: queryKeys.categories.all() });
      toast.success(`Categoria "${updated.name}" actualizada.`);
      onOpenChange(false);
    },
    onError: (error) => {
      const message =
        error instanceof ApiError ? error.message : "No se pudo actualizar la categoria.";
      toast.error(message);
    },
  });

  function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const formData = new FormData(event.currentTarget);

    updateMutation.mutate({
      name: String(formData.get("name") ?? ""),
      sortOrder: Number(formData.get("sortOrder")),
    });
  }

  const apiError = updateMutation.error instanceof ApiError ? updateMutation.error : null;
  const hasFieldErrors = Boolean(apiError?.errors && Object.keys(apiError.errors).length > 0);

  return (
    <Dialog
      open={category !== null}
      onOpenChange={(nextOpen) => {
        onOpenChange(nextOpen);
        if (!nextOpen) {
          updateMutation.reset();
        }
      }}
    >
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Editar categoria</DialogTitle>
          <DialogDescription>Corrige el nombre o el orden de la categoria.</DialogDescription>
        </DialogHeader>
        {category && (
          <form onSubmit={handleSubmit} className="flex flex-col gap-4">
            {apiError && !hasFieldErrors && (
              <Alert variant="destructive">
                <AlertTitle>No se pudo actualizar la categoria</AlertTitle>
                <AlertDescription>{apiError.message}</AlertDescription>
              </Alert>
            )}

            <div className="flex flex-col gap-1.5">
              <Label htmlFor="edit-category-name">Nombre</Label>
              <Input
                id="edit-category-name"
                name="name"
                required
                maxLength={120}
                defaultValue={category.name}
                disabled={updateMutation.isPending}
              />
              <FieldError error={apiError} field="name" />
            </div>

            <div className="flex flex-col gap-1.5">
              <Label htmlFor="edit-category-sortOrder">Orden</Label>
              <Input
                id="edit-category-sortOrder"
                name="sortOrder"
                type="number"
                min={0}
                required
                defaultValue={category.sortOrder}
                disabled={updateMutation.isPending}
              />
              <FieldError error={apiError} field="sortOrder" />
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
