"use client";

import { useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";

import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
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
import { queryKeys } from "@/lib/query-keys";
import { cn } from "@/lib/utils";
import type { InventoryItemResponse, UpdateInventoryItemStatusRequest } from "@/lib/types";
import { CreateItemDialog } from "./create-item-dialog";
import { EditItemDialog } from "./edit-item-dialog";

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
                emptyMessage="Todavia no hay insumos. Crea el primero con el boton de arriba."
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

      </Tabs>

      <EditItemDialog
        key={editingItem?.id ?? "none"}
        item={editingItem}
        onOpenChange={(open) => {
          if (!open) {
            setEditingItem(null);
          }
        }}
      />
    </div>
  );
}
