"use client";

import { useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
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
import {
  USER_ROLE_LABELS,
  type CategoryResponse,
  type OrderSummaryResponse,
  type ProductResponse,
  type TableResponse,
  type UpdateProductStatusRequest,
  type UpdateTableStatusRequest,
  type UpdateUserStatusRequest,
  type UserResponse,
} from "@/lib/types";
import { CreateCategoryDialog } from "./create-category-dialog";
import { CreateProductDialog } from "./create-product-dialog";
import { CreateTableDialog } from "./create-table-dialog";
import { CreateUserDialog } from "./create-user-dialog";
import { EditCategoryDialog } from "./edit-category-dialog";
import { EditProductDialog } from "./edit-product-dialog";
import { EditTableDialog } from "./edit-table-dialog";

/** Considera "de hoy" cualquier orden abierta en el dia local del navegador. */
function isToday(iso: string): boolean {
  const date = new Date(iso);
  const now = new Date();
  return (
    date.getFullYear() === now.getFullYear() &&
    date.getMonth() === now.getMonth() &&
    date.getDate() === now.getDate()
  );
}

/**
 * Dashboard de administracion. No existe un endpoint de estadisticas, asi
 * que los conteos se derivan de las mismas listas que alimentan las tablas:
 * mesas, categorias, productos y ordenes de hoy con su total.
 */
export function AdminView() {
  const { user: currentUser } = useSession();
  const queryClient = useQueryClient();
  const [editingTable, setEditingTable] = useState<TableResponse | null>(null);
  const [editingCategory, setEditingCategory] = useState<CategoryResponse | null>(null);
  const [editingProduct, setEditingProduct] = useState<ProductResponse | null>(null);
  /** A diferencia del mesero o el mapa de mesas, la administracion necesita
   * ver (y reactivar) las filas dadas de baja, asi que pide el catalogo
   * completo con `includeInactive = true`. */
  const tablesQuery = useQuery({
    queryKey: queryKeys.tables.all(true),
    queryFn: () => api.get<TableResponse[]>(endpoints.tables.list(), { includeInactive: true }),
  });
  const categoriesQuery = useQuery({
    queryKey: queryKeys.categories.all(),
    queryFn: () => api.get<CategoryResponse[]>(endpoints.categories.list()),
  });
  const productsQuery = useQuery({
    queryKey: queryKeys.products.all(true),
    queryFn: () => api.get<ProductResponse[]>(endpoints.products.list(), { includeInactive: true }),
  });
  const ordersQuery = useQuery({
    queryKey: queryKeys.orders.all(),
    queryFn: () => api.get<OrderSummaryResponse[]>(endpoints.orders.list()),
  });
  const usersQuery = useQuery({
    queryKey: queryKeys.users.all(),
    queryFn: () => api.get<UserResponse[]>(endpoints.users.list()),
  });

  const toggleUserActiveMutation = useMutation({
    mutationFn: ({ userId, active }: { userId: string; active: boolean }) =>
      api.patch<UserResponse>(endpoints.users.updateStatus(userId), {
        active,
      } satisfies UpdateUserStatusRequest),
    onSuccess: (user) => {
      queryClient.invalidateQueries({ queryKey: queryKeys.users.all() });
      toast.success(
        user.active ? `Usuario "${user.fullName}" activado.` : `Usuario "${user.fullName}" desactivado.`,
      );
    },
    onError: (error) => {
      const message =
        error instanceof ApiError ? error.message : "No se pudo actualizar el usuario.";
      toast.error(message);
    },
  });

  /** Da de alta o de baja una mesa. La lista de administracion pide
   * `includeInactive = true`, asi que invalidarla y refetchear muestra la
   * fila recien desactivada (marcada como inactiva) en vez de esconderla. */
  const toggleTableActiveMutation = useMutation({
    mutationFn: ({ tableId, active }: { tableId: string; active: boolean }) =>
      api.patch<TableResponse>(endpoints.tables.updateStatus(tableId), {
        active,
      } satisfies UpdateTableStatusRequest),
    onSuccess: (table) => {
      queryClient.invalidateQueries({ queryKey: queryKeys.tables.all(true) });
      toast.success(
        table.active ? `Mesa ${table.number} activada.` : `Mesa ${table.number} desactivada.`,
      );
    },
    onError: (error) => {
      const message =
        error instanceof ApiError ? error.message : "No se pudo actualizar la mesa.";
      toast.error(message);
    },
  });

  /** Igual razonamiento que {@link toggleTableActiveMutation}. */
  const toggleProductActiveMutation = useMutation({
    mutationFn: ({ productId, active }: { productId: string; active: boolean }) =>
      api.patch<ProductResponse>(endpoints.products.updateStatus(productId), {
        active,
      } satisfies UpdateProductStatusRequest),
    onSuccess: (product) => {
      queryClient.invalidateQueries({ queryKey: queryKeys.products.all(true) });
      toast.success(
        product.active
          ? `Producto "${product.name}" activado.`
          : `Producto "${product.name}" desactivado.`,
      );
    },
    onError: (error) => {
      const message =
        error instanceof ApiError ? error.message : "No se pudo actualizar el producto.";
      toast.error(message);
    },
  });

  const todayOrders = useMemo(
    () => (ordersQuery.data ?? []).filter((order) => isToday(order.openedAt)),
    [ordersQuery.data],
  );
  const todayTotal = useMemo(
    () => todayOrders.reduce((sum, order) => sum + order.total, 0),
    [todayOrders],
  );

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-xl font-semibold">Administracion</h1>
        <p className="text-sm text-muted-foreground">
          Resumen del restaurante, y alta de mesas, categorias, productos y usuarios.
        </p>
      </div>

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-5">
        <SummaryCard
          label="Mesas"
          value={tablesQuery.data?.length}
          isLoading={tablesQuery.isLoading}
        />
        <SummaryCard
          label="Categorias"
          value={categoriesQuery.data?.length}
          isLoading={categoriesQuery.isLoading}
        />
        <SummaryCard
          label="Productos"
          value={productsQuery.data?.length}
          isLoading={productsQuery.isLoading}
        />
        <SummaryCard
          label="Usuarios"
          value={usersQuery.data?.length}
          isLoading={usersQuery.isLoading}
        />
        <Card>
          <CardHeader>
            <CardDescription>Ordenes de hoy</CardDescription>
            <CardTitle className="text-2xl">
              {ordersQuery.isLoading ? "…" : todayOrders.length}
            </CardTitle>
          </CardHeader>
          <CardContent>
            <p className="text-sm text-muted-foreground">
              Total:{" "}
              {ordersQuery.isLoading ? "…" : <Money amount={todayTotal} />}
            </p>
          </CardContent>
        </Card>
      </div>

      <Tabs defaultValue="tables">
        <TabsList>
          <TabsTrigger value="tables">Mesas</TabsTrigger>
          <TabsTrigger value="categories">Categorias</TabsTrigger>
          <TabsTrigger value="products">Productos</TabsTrigger>
          <TabsTrigger value="users">Usuarios</TabsTrigger>
        </TabsList>

        <TabsContent value="tables" className="flex flex-col gap-4">
          <div className="flex justify-end">
            <CreateTableDialog />
          </div>
          <Card>
            <CardContent>
              <QueryState
                isLoading={tablesQuery.isLoading}
                error={tablesQuery.error}
                isEmpty={tablesQuery.data?.length === 0}
                emptyMessage="Todavia no hay mesas. Crea la primera con el boton de arriba."
              >
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>Numero</TableHead>
                      <TableHead>Capacidad</TableHead>
                      <TableHead>Ocupacion</TableHead>
                      <TableHead>Alta</TableHead>
                      <TableHead />
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {tablesQuery.data?.map((table) => {
                      const isTogglingThisRow =
                        toggleTableActiveMutation.isPending &&
                        toggleTableActiveMutation.variables?.tableId === table.id;
                      return (
                        <TableRow key={table.id}>
                          <TableCell>{table.number}</TableCell>
                          <TableCell>{table.capacity}</TableCell>
                          <TableCell>
                            <StatusBadge kind="table" status={table.status} />
                          </TableCell>
                          <TableCell>
                            <Badge variant={table.active ? "secondary" : "outline"}>
                              {table.active ? "Activa" : "Inactiva"}
                            </Badge>
                          </TableCell>
                          <TableCell className="flex justify-end gap-2 text-right">
                            <Button
                              variant="outline"
                              size="sm"
                              onClick={() => setEditingTable(table)}
                            >
                              Editar
                            </Button>
                            <Button
                              variant={table.active ? "destructive" : "outline"}
                              size="sm"
                              disabled={isTogglingThisRow}
                              onClick={() =>
                                toggleTableActiveMutation.mutate({
                                  tableId: table.id,
                                  active: !table.active,
                                })
                              }
                            >
                              {isTogglingThisRow
                                ? "Guardando..."
                                : table.active
                                  ? "Dar de baja"
                                  : "Reactivar"}
                            </Button>
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

        <TabsContent value="categories" className="flex flex-col gap-4">
          <div className="flex justify-end">
            <CreateCategoryDialog />
          </div>
          <Card>
            <CardContent>
              <QueryState
                isLoading={categoriesQuery.isLoading}
                error={categoriesQuery.error}
                isEmpty={categoriesQuery.data?.length === 0}
                emptyMessage="Todavia no hay categorias. Crea la primera con el boton de arriba."
              >
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>Nombre</TableHead>
                      <TableHead>Orden</TableHead>
                      <TableHead />
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {categoriesQuery.data?.map((category) => (
                      <TableRow key={category.id}>
                        <TableCell>{category.name}</TableCell>
                        <TableCell>{category.sortOrder}</TableCell>
                        <TableCell className="text-right">
                          <Button
                            variant="outline"
                            size="sm"
                            onClick={() => setEditingCategory(category)}
                          >
                            Editar
                          </Button>
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </QueryState>
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="products" className="flex flex-col gap-4">
          <div className="flex justify-end">
            <CreateProductDialog categories={categoriesQuery.data ?? []} />
          </div>
          <Card>
            <CardContent>
              <QueryState
                isLoading={productsQuery.isLoading}
                error={productsQuery.error}
                isEmpty={productsQuery.data?.length === 0}
                emptyMessage="Todavia no hay productos. Crea el primero con el boton de arriba."
              >
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>Nombre</TableHead>
                      <TableHead>Descripcion</TableHead>
                      <TableHead>Precio</TableHead>
                      <TableHead>Categoria</TableHead>
                      <TableHead>Alta</TableHead>
                      <TableHead />
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {productsQuery.data?.map((product) => {
                      const isTogglingThisRow =
                        toggleProductActiveMutation.isPending &&
                        toggleProductActiveMutation.variables?.productId === product.id;
                      return (
                        <TableRow key={product.id}>
                          <TableCell>{product.name}</TableCell>
                          <TableCell>{product.description ?? "-"}</TableCell>
                          <TableCell>
                            <Money amount={product.price} />
                          </TableCell>
                          <TableCell>
                            {categoriesQuery.data?.find((category) => category.id === product.categoryId)
                              ?.name ?? "Sin categoria"}
                          </TableCell>
                          <TableCell>
                            <Badge variant={product.active ? "secondary" : "outline"}>
                              {product.active ? "Activo" : "Inactivo"}
                            </Badge>
                          </TableCell>
                          <TableCell className="flex justify-end gap-2 text-right">
                            <Button
                              variant="outline"
                              size="sm"
                              onClick={() => setEditingProduct(product)}
                            >
                              Editar
                            </Button>
                            <Button
                              variant={product.active ? "destructive" : "outline"}
                              size="sm"
                              disabled={isTogglingThisRow}
                              onClick={() =>
                                toggleProductActiveMutation.mutate({
                                  productId: product.id,
                                  active: !product.active,
                                })
                              }
                            >
                              {isTogglingThisRow
                                ? "Guardando..."
                                : product.active
                                  ? "Dar de baja"
                                  : "Reactivar"}
                            </Button>
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

        <TabsContent value="users" className="flex flex-col gap-4">
          <div className="flex justify-end">
            <CreateUserDialog />
          </div>
          <Card>
            <CardContent>
              <QueryState
                isLoading={usersQuery.isLoading}
                error={usersQuery.error}
                isEmpty={usersQuery.data?.length === 0}
                emptyMessage="Todavia no hay usuarios. Crea el primero con el boton de arriba."
              >
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>Nombre</TableHead>
                      <TableHead>Correo</TableHead>
                      <TableHead>Rol</TableHead>
                      <TableHead>Estado</TableHead>
                      <TableHead />
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {usersQuery.data?.map((user) => {
                      const isTogglingThisRow =
                        toggleUserActiveMutation.isPending &&
                        toggleUserActiveMutation.variables?.userId === user.id;
                      return (
                        <TableRow key={user.id}>
                          <TableCell>
                            {user.fullName}
                            {user.id === currentUser?.userId && (
                              <span className="ml-1.5 text-xs text-muted-foreground">(tu)</span>
                            )}
                          </TableCell>
                          <TableCell>{user.email}</TableCell>
                          <TableCell>
                            <Badge variant="secondary">{USER_ROLE_LABELS[user.role]}</Badge>
                          </TableCell>
                          <TableCell>
                            <Badge variant={user.active ? "secondary" : "outline"}>
                              {user.active ? "Activo" : "Inactivo"}
                            </Badge>
                          </TableCell>
                          <TableCell className="text-right">
                            <Button
                              variant={user.active ? "destructive" : "outline"}
                              size="sm"
                              disabled={isTogglingThisRow}
                              onClick={() =>
                                toggleUserActiveMutation.mutate({
                                  userId: user.id,
                                  active: !user.active,
                                })
                              }
                            >
                              {isTogglingThisRow
                                ? "Guardando..."
                                : user.active
                                  ? "Desactivar"
                                  : "Activar"}
                            </Button>
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

      <EditTableDialog
        table={editingTable}
        onOpenChange={(open) => {
          if (!open) {
            setEditingTable(null);
          }
        }}
      />
      <EditCategoryDialog
        category={editingCategory}
        onOpenChange={(open) => {
          if (!open) {
            setEditingCategory(null);
          }
        }}
      />
      <EditProductDialog
        key={editingProduct?.id ?? "none"}
        product={editingProduct}
        categories={categoriesQuery.data ?? []}
        onOpenChange={(open) => {
          if (!open) {
            setEditingProduct(null);
          }
        }}
      />
    </div>
  );
}

interface SummaryCardProps {
  label: string;
  value: number | undefined;
  isLoading: boolean;
}

function SummaryCard({ label, value, isLoading }: SummaryCardProps) {
  return (
    <Card>
      <CardHeader>
        <CardDescription>{label}</CardDescription>
        <CardTitle className="text-2xl">{isLoading ? "…" : (value ?? 0)}</CardTitle>
      </CardHeader>
    </Card>
  );
}
