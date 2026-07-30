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
import type { CategoryResponse, CreateCategoryRequest } from "@/lib/types";

export function CreateCategoryDialog() {
  const [open, setOpen] = useState(false);
  const queryClient = useQueryClient();

  const createMutation = useMutation({
    mutationFn: (payload: CreateCategoryRequest) =>
      api.post<CategoryResponse>(endpoints.categories.create(), payload),
    onSuccess: (category) => {
      queryClient.invalidateQueries({ queryKey: queryKeys.categories.all() });
      toast.success(`Categoria "${category.name}" creada.`);
      setOpen(false);
    },
  });

  function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const formData = new FormData(event.currentTarget);
    const sortOrderRaw = String(formData.get("sortOrder") ?? "").trim();

    createMutation.mutate({
      name: String(formData.get("name") ?? ""),
      sortOrder: sortOrderRaw === "" ? undefined : Number(sortOrderRaw),
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
        <Button>Nueva categoria</Button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Nueva categoria</DialogTitle>
          <DialogDescription>Agrupa los productos del menu.</DialogDescription>
        </DialogHeader>
        <form onSubmit={handleSubmit} className="flex flex-col gap-4">
          {apiError && !hasFieldErrors && (
            <Alert variant="destructive">
              <AlertTitle>No se pudo crear la categoria</AlertTitle>
              <AlertDescription>{apiError.message}</AlertDescription>
            </Alert>
          )}

          <div className="flex flex-col gap-1.5">
            <Label htmlFor="name">Nombre</Label>
            <Input
              id="name"
              name="name"
              required
              maxLength={120}
              disabled={createMutation.isPending}
            />
            <FieldError error={apiError} field="name" />
          </div>

          <div className="flex flex-col gap-1.5">
            <Label htmlFor="sortOrder">Orden (opcional)</Label>
            <Input
              id="sortOrder"
              name="sortOrder"
              type="number"
              min={0}
              disabled={createMutation.isPending}
            />
            <FieldError error={apiError} field="sortOrder" />
          </div>

          <DialogFooter>
            <Button type="submit" disabled={createMutation.isPending}>
              {createMutation.isPending ? "Guardando..." : "Crear categoria"}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
