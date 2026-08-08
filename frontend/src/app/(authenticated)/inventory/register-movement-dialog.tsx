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
import {
  MOVEMENT_TYPE_LABELS,
  type InventoryItemResponse,
  type InventoryMovementType,
  type RegisterMovementRequest,
  type RegisteredMovementResponse,
} from "@/lib/types";

interface RegisterMovementDialogProps {
  item: InventoryItemResponse | null;
  onOpenChange: (open: boolean) => void;
}

const MOVEMENT_TYPES: InventoryMovementType[] = ["IN", "OUT", "WASTE", "ADJUSTMENT"];

/**
 * Registro de un movimiento. El tipo decide que campo se pide, porque el
 * backend acepta `quantity` en entradas, salidas y mermas, y `countedStock`
 * solo en ajustes -- nunca los dos.
 *
 * En un ajuste se muestra la diferencia estimada contra el stock actual, pero
 * lo que viaja es el conteo: el delta lo calcula el servidor con la fila
 * bloqueada. Si este cliente mandara el delta, lo habria calculado sobre un
 * stock que ya pudo cambiar.
 */
export function RegisterMovementDialog({ item, onOpenChange }: RegisterMovementDialogProps) {
  const queryClient = useQueryClient();
  const [movementType, setMovementType] = useState<InventoryMovementType>("IN");
  const [countedStock, setCountedStock] = useState("");

  const isAdjustment = movementType === "ADJUSTMENT";
  const requiresReason = movementType === "WASTE";

  const registerMutation = useMutation({
    mutationFn: (payload: RegisterMovementRequest) =>
      api.post<RegisteredMovementResponse>(endpoints.inventory.registerMovement(), payload),
    onSuccess: (movement) => {
      queryClient.invalidateQueries({ queryKey: queryKeys.inventory.items(true) });
      queryClient.invalidateQueries({ queryKey: queryKeys.inventory.items() });
      queryClient.invalidateQueries({ queryKey: ["inventory", "movements"] });
      toast.success(
        `${MOVEMENT_TYPE_LABELS[movement.movementType]} registrada: ${movement.quantity} ${item?.unit ?? ""}.`,
      );
      close();
    },
  });

  function close() {
    registerMutation.reset();
    setMovementType("IN");
    setCountedStock("");
    onOpenChange(false);
  }

  function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!item) {
      return;
    }
    const formData = new FormData(event.currentTarget);
    const reason = String(formData.get("reason") ?? "").trim();

    registerMutation.mutate({
      inventoryItemId: item.id,
      movementType,
      quantity: isAdjustment ? undefined : Number(formData.get("quantity")),
      countedStock: isAdjustment ? Number(countedStock) : undefined,
      reason: reason === "" ? undefined : reason,
    });
  }

  const apiError = registerMutation.error instanceof ApiError ? registerMutation.error : null;
  const hasFieldErrors = Boolean(apiError?.errors && Object.keys(apiError.errors).length > 0);
  const estimatedDelta =
    item && isAdjustment && countedStock.trim() !== ""
      ? Number(countedStock) - item.stock
      : null;

  return (
    <Dialog
      open={item !== null}
      onOpenChange={(nextOpen) => {
        if (!nextOpen) {
          close();
        }
      }}
    >
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Registrar movimiento</DialogTitle>
          <DialogDescription>
            {item ? `${item.name} — stock actual: ${item.stock} ${item.unit}` : ""}
          </DialogDescription>
        </DialogHeader>
        {item && (
          <form onSubmit={handleSubmit} className="flex flex-col gap-4">
            {apiError && !hasFieldErrors && (
              <Alert variant="destructive">
                <AlertTitle>No se pudo registrar el movimiento</AlertTitle>
                <AlertDescription>{apiError.message}</AlertDescription>
              </Alert>
            )}

            <div className="flex flex-col gap-1.5">
              <Label htmlFor="movementType">Tipo</Label>
              <Select
                value={movementType}
                onValueChange={(value) => setMovementType(value as InventoryMovementType)}
                disabled={registerMutation.isPending}
              >
                <SelectTrigger id="movementType" className="w-full">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {MOVEMENT_TYPES.map((type) => (
                    <SelectItem key={type} value={type}>
                      {MOVEMENT_TYPE_LABELS[type]}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>

            {isAdjustment ? (
              <div className="flex flex-col gap-1.5">
                <Label htmlFor="countedStock">Conteo fisico</Label>
                <Input
                  id="countedStock"
                  name="countedStock"
                  type="number"
                  min={0}
                  step="0.001"
                  required
                  value={countedStock}
                  onChange={(event) => setCountedStock(event.target.value)}
                  disabled={registerMutation.isPending}
                />
                <p className="text-xs text-muted-foreground">
                  {estimatedDelta === null
                    ? "Captura cuanto hay en realidad; el sistema calcula la diferencia."
                    : `Diferencia estimada: ${estimatedDelta > 0 ? "+" : ""}${estimatedDelta.toFixed(3)} ${item.unit}. El valor definitivo lo calcula el servidor.`}
                </p>
                <FieldError error={apiError} field="countedStock" />
              </div>
            ) : (
              <div className="flex flex-col gap-1.5">
                <Label htmlFor="quantity">Cantidad ({item.unit})</Label>
                <Input
                  id="quantity"
                  name="quantity"
                  type="number"
                  min={0}
                  step="0.001"
                  required
                  disabled={registerMutation.isPending}
                />
                <FieldError error={apiError} field="quantity" />
              </div>
            )}

            <div className="flex flex-col gap-1.5">
              <Label htmlFor="reason">
                {requiresReason ? "Motivo" : "Motivo (opcional)"}
              </Label>
              <Textarea
                id="reason"
                name="reason"
                maxLength={150}
                required={requiresReason}
                placeholder={requiresReason ? "Se echo a perder, se quemo, se cayo..." : ""}
                disabled={registerMutation.isPending}
              />
              <FieldError error={apiError} field="reason" />
            </div>

            <DialogFooter>
              <Button type="submit" disabled={registerMutation.isPending}>
                {registerMutation.isPending ? "Guardando..." : "Registrar"}
              </Button>
            </DialogFooter>
          </form>
        )}
      </DialogContent>
    </Dialog>
  );
}
