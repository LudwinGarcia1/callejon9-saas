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
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Textarea } from "@/components/ui/textarea";
import { FieldError } from "@/components/shared/field-error";
import { ApiError, api } from "@/lib/api";
import { endpoints } from "@/lib/endpoints";
import { queryKeys } from "@/lib/query-keys";
import type { CategoryResponse, ProductResponse, UpdateProductRequest } from "@/lib/types";

/** Valor centinela para "sin categoria": Radix Select no admite value="". */
const NO_CATEGORY = "none";

interface EditProductDialogProps {
  /** Producto a editar, o null si el dialogo esta cerrado. */
  product: ProductResponse | null;
  categories: CategoryResponse[];
  onOpenChange: (open: boolean) => void;
}

/**
 * Dialogo para corregir nombre, descripcion, precio o categoria de un
 * producto ya existente. El precio nuevo solo aplica hacia adelante: cada
 * linea de orden ya guarda su propia copia del precio al momento de
 * agregarse, asi que editar aqui nunca altera una orden pasada.
 */
export function EditProductDialog({ product, categories, onOpenChange }: EditProductDialogProps) {
  const [categoryId, setCategoryId] = useState<string>(product?.categoryId ?? NO_CATEGORY);
  const queryClient = useQueryClient();

  const updateMutation = useMutation({
    mutationFn: (payload: UpdateProductRequest) => {
      if (!product) {
        throw new Error("No hay producto seleccionado.");
      }
      return api.put<ProductResponse>(endpoints.products.update(product.id), payload);
    },
    onSuccess: (updated) => {
      queryClient.invalidateQueries({ queryKey: queryKeys.products.all(true) });
      toast.success(`Producto "${updated.name}" actualizado.`);
      onOpenChange(false);
    },
    onError: (error) => {
      const message =
        error instanceof ApiError ? error.message : "No se pudo actualizar el producto.";
      toast.error(message);
    },
  });

  function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const formData = new FormData(event.currentTarget);
    const description = String(formData.get("description") ?? "").trim();

    updateMutation.mutate({
      name: String(formData.get("name") ?? ""),
      description: description === "" ? undefined : description,
      price: Number(formData.get("price")),
      categoryId: categoryId === NO_CATEGORY ? undefined : categoryId,
    });
  }

  const apiError = updateMutation.error instanceof ApiError ? updateMutation.error : null;
  const hasFieldErrors = Boolean(apiError?.errors && Object.keys(apiError.errors).length > 0);

  return (
    <Dialog
      open={product !== null}
      onOpenChange={(nextOpen) => {
        onOpenChange(nextOpen);
        if (!nextOpen) {
          updateMutation.reset();
        }
      }}
    >
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Editar producto</DialogTitle>
          <DialogDescription>Corrige los datos del producto.</DialogDescription>
        </DialogHeader>
        {product && (
          <form
            key={product.id}
            onSubmit={handleSubmit}
            className="flex flex-col gap-4"
          >
            {apiError && !hasFieldErrors && (
              <Alert variant="destructive">
                <AlertTitle>No se pudo actualizar el producto</AlertTitle>
                <AlertDescription>{apiError.message}</AlertDescription>
              </Alert>
            )}

            <div className="flex flex-col gap-1.5">
              <Label htmlFor="edit-product-name">Nombre</Label>
              <Input
                id="edit-product-name"
                name="name"
                required
                maxLength={160}
                defaultValue={product.name}
                disabled={updateMutation.isPending}
              />
              <FieldError error={apiError} field="name" />
            </div>

            <div className="flex flex-col gap-1.5">
              <Label htmlFor="edit-product-description">Descripción (opcional)</Label>
              <Textarea
                id="edit-product-description"
                name="description"
                defaultValue={product.description ?? ""}
                disabled={updateMutation.isPending}
              />
              <FieldError error={apiError} field="description" />
            </div>

            <div className="flex flex-col gap-1.5">
              <Label htmlFor="edit-product-price">Precio</Label>
              <Input
                id="edit-product-price"
                name="price"
                type="number"
                min={0}
                step="0.01"
                required
                defaultValue={product.price}
                disabled={updateMutation.isPending}
              />
              <FieldError error={apiError} field="price" />
            </div>

            <div className="flex flex-col gap-1.5">
              <Label htmlFor="edit-product-category">Categoría (opcional)</Label>
              <Select
                value={categoryId}
                onValueChange={setCategoryId}
                disabled={updateMutation.isPending}
              >
                <SelectTrigger id="edit-product-category" className="w-full">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value={NO_CATEGORY}>Sin categoría</SelectItem>
                  {categories.map((category) => (
                    <SelectItem key={category.id} value={category.id}>
                      {category.name}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
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
