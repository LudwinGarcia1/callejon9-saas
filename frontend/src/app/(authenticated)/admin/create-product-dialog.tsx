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
import type { CategoryResponse, CreateProductRequest, ProductResponse } from "@/lib/types";

/** Valor centinela para "sin categoria": Radix Select no admite value="". */
const NO_CATEGORY = "none";

interface CreateProductDialogProps {
  categories: CategoryResponse[];
}

/** Formulario de alta de producto. `categoryId` viene de un Select de shadcn,
 * que no participa de FormData, asi que se guarda en estado y se combina con
 * el resto del payload al enviar. */
export function CreateProductDialog({ categories }: CreateProductDialogProps) {
  const [open, setOpen] = useState(false);
  const [categoryId, setCategoryId] = useState<string>(NO_CATEGORY);
  const queryClient = useQueryClient();

  const createMutation = useMutation({
    mutationFn: (payload: CreateProductRequest) =>
      api.post<ProductResponse>(endpoints.products.create(), payload),
    onSuccess: (product) => {
      queryClient.invalidateQueries({ queryKey: queryKeys.products.all() });
      toast.success(`Producto "${product.name}" creado.`);
      setOpen(false);
      setCategoryId(NO_CATEGORY);
    },
  });

  function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const formData = new FormData(event.currentTarget);
    const description = String(formData.get("description") ?? "").trim();

    createMutation.mutate({
      name: String(formData.get("name") ?? ""),
      description: description === "" ? undefined : description,
      price: Number(formData.get("price")),
      categoryId: categoryId === NO_CATEGORY ? undefined : categoryId,
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
        <Button>Nuevo producto</Button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Nuevo producto</DialogTitle>
          <DialogDescription>Agrega un producto al menú.</DialogDescription>
        </DialogHeader>
        <form onSubmit={handleSubmit} className="flex flex-col gap-4">
          {apiError && !hasFieldErrors && (
            <Alert variant="destructive">
              <AlertTitle>No se pudo crear el producto</AlertTitle>
              <AlertDescription>{apiError.message}</AlertDescription>
            </Alert>
          )}

          <div className="flex flex-col gap-1.5">
            <Label htmlFor="name">Nombre</Label>
            <Input
              id="name"
              name="name"
              required
              maxLength={160}
              disabled={createMutation.isPending}
            />
            <FieldError error={apiError} field="name" />
          </div>

          <div className="flex flex-col gap-1.5">
            <Label htmlFor="description">Descripción (opcional)</Label>
            <Textarea id="description" name="description" disabled={createMutation.isPending} />
            <FieldError error={apiError} field="description" />
          </div>

          <div className="flex flex-col gap-1.5">
            <Label htmlFor="price">Precio</Label>
            <Input
              id="price"
              name="price"
              type="number"
              min={0}
              step="0.01"
              required
              disabled={createMutation.isPending}
            />
            <FieldError error={apiError} field="price" />
          </div>

          <div className="flex flex-col gap-1.5">
            <Label htmlFor="categoryId">Categoría (opcional)</Label>
            <Select
              value={categoryId}
              onValueChange={setCategoryId}
              disabled={createMutation.isPending}
            >
              <SelectTrigger id="categoryId" className="w-full">
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
            <Button type="submit" size="lg" disabled={createMutation.isPending}>
              {createMutation.isPending ? "Guardando…" : "Crear producto"}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
