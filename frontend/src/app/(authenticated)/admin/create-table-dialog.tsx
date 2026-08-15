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
import type { CreateTableRequest, TableResponse } from "@/lib/types";

/** Formulario de alta de mesa. Un numero repetido llega como 409 generico
 * (violacion de restriccion UNIQUE), no como error de campo. */
export function CreateTableDialog() {
  const [open, setOpen] = useState(false);
  const queryClient = useQueryClient();

  const createMutation = useMutation({
    mutationFn: (payload: CreateTableRequest) =>
      api.post<TableResponse>(endpoints.tables.create(), payload),
    onSuccess: (table) => {
      queryClient.invalidateQueries({ queryKey: queryKeys.tables.all() });
      toast.success(`Mesa ${table.number} creada.`);
      setOpen(false);
    },
  });

  function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const formData = new FormData(event.currentTarget);

    createMutation.mutate({
      number: Number(formData.get("number")),
      capacity: Number(formData.get("capacity")),
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
        <Button>Nueva mesa</Button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Nueva mesa</DialogTitle>
          <DialogDescription>Agrega una mesa al salón.</DialogDescription>
        </DialogHeader>
        <form onSubmit={handleSubmit} className="flex flex-col gap-4">
          {apiError && !hasFieldErrors && (
            <Alert variant="destructive">
              <AlertTitle>No se pudo crear la mesa</AlertTitle>
              <AlertDescription>{apiError.message}</AlertDescription>
            </Alert>
          )}

          <div className="flex flex-col gap-1.5">
            <Label htmlFor="number">Número</Label>
            <Input
              id="number"
              name="number"
              type="number"
              min={1}
              required
              disabled={createMutation.isPending}
            />
            <FieldError error={apiError} field="number" />
          </div>

          <div className="flex flex-col gap-1.5">
            <Label htmlFor="capacity">Capacidad</Label>
            <Input
              id="capacity"
              name="capacity"
              type="number"
              min={1}
              required
              disabled={createMutation.isPending}
            />
            <FieldError error={apiError} field="capacity" />
          </div>

          <DialogFooter>
            <Button type="submit" size="lg" disabled={createMutation.isPending}>
              {createMutation.isPending ? "Guardando…" : "Crear mesa"}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
