"use client";

import { useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";

import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Money } from "@/components/shared/money";
import { QueryState } from "@/components/shared/query-state";
import { StatusBadge } from "@/components/shared/status-badge";
import { useSession } from "@/hooks/use-session";
import { ApiError, api } from "@/lib/api";
import { endpoints } from "@/lib/endpoints";
import { formatShortDate, formatShortTime, todayIsoDate } from "@/lib/format";
import { queryKeys } from "@/lib/query-keys";
import { cn } from "@/lib/utils";
import type {
  InventoryItemResponse,
  InventoryMovementRow,
  UpdateInventoryItemStatusRequest,
} from "@/lib/types";
import { CreateItemDialog } from "./create-item-dialog";
import { EditItemDialog } from "./edit-item-dialog";
import { RegisterMovementDialog } from "./register-movement-dialog";

/**
 * Pantalla de inventario. ADMIN administra el catalogo y registra movimientos;
 * KITCHEN solo registra movimientos, porque el alta y la edicion de insumos son
 * endpoints de ADMIN. La interfaz oculta lo que el servidor rechazaria en vez
 * de ofrecer botones que devuelven 403.
 */
export function InventoryView() {
  const { user } = useSession();
  const queryClient = useQueryClient();
  const [editingItem, setEditingItem] = useState<InventoryItemResponse | null>(null);
  const [movingItem, setMovingItem] = useState<InventoryItemResponse | null>(null);

  const canManageCatalog = user?.role === "ADMIN";

  const itemsQuery = useQuery({
    queryKey: queryKeys.inventory.items(canManageCatalog),
    queryFn: () =>
      api.get<InventoryItemResponse[]>(endpoints.inventory.items(), {
        includeInactive: canManageCatalog,
      }),
  });

  const toggleActiveMutation = useMutation({
    mutationFn: ({ itemId, active }: { itemId: string; active: boolean }) =>
      api.patch<InventoryItemResponse>(endpoints.inventory.updateItemStatus(itemId), {
        active,
      } satisfies UpdateInventoryItemStatusRequest),
    onSuccess: (item) => {
      queryClient.invalidateQueries({ queryKey: queryKeys.inventory.items(true) });
      toast.success(
        item.active ? `Insumo "${item.name}" activado.` : `Insumo "${item.name}" dado de baja.`,
      );
    },
    onError: (error) => {
      toast.error(
        error instanceof ApiError ? error.message : "No se pudo actualizar el insumo.",
      );
    },
  });

  const items = useMemo(() => itemsQuery.data ?? [], [itemsQuery.data]);

  const alertCount = useMemo(
    () => items.filter((item) => item.level !== "OK").length,
    [items],
  );

  /** Valor del inventario: es lo que le da sentido a capturar el costo unitario. */
  const inventoryValue = useMemo(
    () => items.reduce((sum, item) => sum + item.stock * item.unitCost, 0),
    [items],
  );

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-xl font-semibold">Inventario</h1>
        <p className="text-sm text-muted-foreground">
          Insumos, entradas, salidas, mermas y ajustes por conteo fisico.
        </p>
      </div>

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
        <Card>
          <CardHeader>
            <CardDescription>Insumos</CardDescription>
            <CardTitle className="text-2xl">
              {itemsQuery.isLoading ? "…" : items.length}
            </CardTitle>
          </CardHeader>
        </Card>
        <Card>
          <CardHeader>
            <CardDescription>En alerta</CardDescription>
            <CardTitle className="text-2xl">
              {itemsQuery.isLoading ? "…" : alertCount}
            </CardTitle>
          </CardHeader>
        </Card>
        <Card>
          <CardHeader>
            <CardDescription>Valor del inventario</CardDescription>
            <CardTitle className="text-2xl">
              {itemsQuery.isLoading ? "…" : <Money amount={inventoryValue} />}
            </CardTitle>
          </CardHeader>
        </Card>
      </div>

      <Tabs defaultValue="items">
        <TabsList>
          <TabsTrigger value="items">Insumos</TabsTrigger>
          <TabsTrigger value="movements">Movimientos</TabsTrigger>
        </TabsList>

        <TabsContent value="items" className="flex flex-col gap-4">
          {canManageCatalog && (
            <div className="flex justify-end">
              <CreateItemDialog />
            </div>
          )}
          <Card>
            <CardContent>
              <QueryState
                isLoading={itemsQuery.isLoading}
                error={itemsQuery.error}
                isEmpty={items.length === 0}
                emptyMessage={
                  canManageCatalog
                    ? "Todavia no hay insumos. Crea el primero con el boton de arriba."
                    : "Todavia no hay insumos. Pide a un administrador que los de de alta."
                }
              >
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>Nombre</TableHead>
                      <TableHead>Stock</TableHead>
                      <TableHead>Nivel</TableHead>
                      <TableHead>Minimo</TableHead>
                      <TableHead>Costo</TableHead>
                      {canManageCatalog && <TableHead>Alta</TableHead>}
                      <TableHead />
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {items.map((item) => {
                      const isTogglingThisRow =
                        toggleActiveMutation.isPending &&
                        toggleActiveMutation.variables?.itemId === item.id;
                      return (
                        <TableRow
                          key={item.id}
                          className={cn(item.level === "NEGATIVE" && "bg-destructive/10")}
                        >
                          <TableCell>{item.name}</TableCell>
                          <TableCell>
                            {item.stock} {item.unit}
                          </TableCell>
                          <TableCell>
                            <StatusBadge kind="stock" status={item.level} />
                          </TableCell>
                          <TableCell>
                            {item.minStock > 0 ? `${item.minStock} ${item.unit}` : "—"}
                          </TableCell>
                          <TableCell>
                            <Money amount={item.unitCost} />
                          </TableCell>
                          {canManageCatalog && (
                            <TableCell>
                              <Badge variant={item.active ? "secondary" : "outline"}>
                                {item.active ? "Activo" : "Inactivo"}
                              </Badge>
                            </TableCell>
                          )}
                          <TableCell className="flex justify-end gap-2 text-right">
                            <Button
                              size="sm"
                              disabled={!item.active}
                              onClick={() => setMovingItem(item)}
                            >
                              Movimiento
                            </Button>
                            {canManageCatalog && (
                              <>
                                <Button
                                  variant="outline"
                                  size="sm"
                                  onClick={() => setEditingItem(item)}
                                >
                                  Editar
                                </Button>
                                <Button
                                  variant={item.active ? "destructive" : "outline"}
                                  size="sm"
                                  disabled={isTogglingThisRow}
                                  onClick={() =>
                                    toggleActiveMutation.mutate({
                                      itemId: item.id,
                                      active: !item.active,
                                    })
                                  }
                                >
                                  {isTogglingThisRow
                                    ? "Guardando..."
                                    : item.active
                                      ? "Dar de baja"
                                      : "Reactivar"}
                                </Button>
                              </>
                            )}
                          </TableCell>
                        </TableRow>
                      );
                    })}
                  </TableBody>
                </Table>
              </QueryState>
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="movements" className="flex flex-col gap-4">
          <MovementsPanel items={items} />
        </TabsContent>
      </Tabs>

      {/*
        La key fuerza el remontaje al cambiar de insumo, para que los
        defaultValue no arrastren los del anterior. Va prefijada por dialogo
        porque los dos son hermanos: sin el prefijo, con ambos cerrados los dos
        caerian en la misma key centinela y React los tratara como el mismo
        hijo.
      */}
      <EditItemDialog
        key={`edit-${editingItem?.id ?? "none"}`}
        item={editingItem}
        onOpenChange={(open) => {
          if (!open) {
            setEditingItem(null);
          }
        }}
      />

      <RegisterMovementDialog
        key={`move-${movingItem?.id ?? "none"}`}
        item={movingItem}
        onOpenChange={(open) => {
          if (!open) {
            setMovingItem(null);
          }
        }}
      />
    </div>
  );
}

interface MovementsPanelProps {
  items: InventoryItemResponse[];
}

/** Valor centinela para "todos los insumos": Radix Select no admite value="". */
const ALL_ITEMS = "all";

/**
 * Historial del ledger. El rango se resuelve en la zona del negocio del lado
 * del servidor; aqui solo se mandan las fechas. Mismo formulario no controlado
 * que el historial de ventas.
 */
function MovementsPanel({ items }: MovementsPanelProps) {
  const today = todayIsoDate();
  const [range, setRange] = useState({ from: today, to: today });
  const [itemId, setItemId] = useState<string>(ALL_ITEMS);

  const movementsQuery = useQuery({
    queryKey: queryKeys.inventory.movements(
      range.from,
      range.to,
      itemId === ALL_ITEMS ? undefined : itemId,
    ),
    queryFn: () =>
      api.get<InventoryMovementRow[]>(endpoints.inventory.movements(), {
        from: range.from,
        to: range.to,
        itemId: itemId === ALL_ITEMS ? undefined : itemId,
      }),
  });

  function handleRangeSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const formData = new FormData(event.currentTarget);
    setRange({
      from: String(formData.get("from") || today),
      to: String(formData.get("to") || today),
    });
  }

  const movements = movementsQuery.data ?? [];

  return (
    <>
      <Card>
        <CardHeader>
          <CardTitle>Filtros</CardTitle>
        </CardHeader>
        <CardContent>
          <form onSubmit={handleRangeSubmit} className="flex flex-wrap items-end gap-3">
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="movements-from">Desde</Label>
              <Input id="movements-from" name="from" type="date" defaultValue={today} required />
            </div>
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="movements-to">Hasta</Label>
              <Input id="movements-to" name="to" type="date" defaultValue={today} required />
            </div>
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="movements-item">Insumo</Label>
              <Select value={itemId} onValueChange={setItemId}>
                <SelectTrigger id="movements-item" className="w-56">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value={ALL_ITEMS}>Todos</SelectItem>
                  {items.map((item) => (
                    <SelectItem key={item.id} value={item.id}>
                      {item.name}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            <Button type="submit">Buscar</Button>
          </form>
        </CardContent>
      </Card>

      <Card>
        <CardContent>
          <QueryState
            isLoading={movementsQuery.isLoading}
            error={movementsQuery.error}
            isEmpty={movements.length === 0}
            emptyMessage="No hay movimientos en el rango seleccionado."
          >
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Fecha</TableHead>
                  <TableHead>Insumo</TableHead>
                  <TableHead>Tipo</TableHead>
                  <TableHead>Cantidad</TableHead>
                  <TableHead>Motivo</TableHead>
                  <TableHead>Registro</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {movements.map((movement) => (
                  <TableRow key={movement.id}>
                    <TableCell>
                      {formatShortDate(movement.createdAt)} {formatShortTime(movement.createdAt)}
                    </TableCell>
                    <TableCell>{movement.itemName}</TableCell>
                    <TableCell>
                      <StatusBadge kind="movement" status={movement.movementType} />
                    </TableCell>
                    <TableCell
                      className={cn(
                        movement.signedQuantity < 0 ? "text-destructive" : "text-foreground",
                      )}
                    >
                      {movement.signedQuantity > 0 ? "+" : ""}
                      {movement.signedQuantity} {movement.unit}
                    </TableCell>
                    <TableCell>{movement.reason ?? "—"}</TableCell>
                    <TableCell>{movement.userName ?? "—"}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </QueryState>
        </CardContent>
      </Card>
    </>
  );
}
