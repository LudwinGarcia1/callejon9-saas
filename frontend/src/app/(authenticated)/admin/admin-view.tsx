"use client";

import { useState } from "react";
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
import { ScreenMetric, ScreenShell } from "@/components/layout/screen-shell";
import { Money } from "@/components/shared/money";
import { QueryState } from "@/components/shared/query-state";
import { StatusBadge } from "@/components/shared/status-badge";
import { useSession } from "@/hooks/use-session";
import { ApiError, api } from "@/lib/api";
import { endpoints } from "@/lib/endpoints";
import { todayIsoDate } from "@/lib/format";
import { queryKeys } from "@/lib/query-keys";
import {
  USER_ROLE_LABELS,
  type AnalyticsResponse,
  type CategoryResponse,
  type ProductResponse,
  type SalesHistoryResponse,
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
import { PrintTablesQrDialog } from "./print-tables-qr-dialog";
import { RestaurantSettingsSection } from "./restaurant-settings-section";

/**
 * Dashboard de administracion. Los conteos de mesas, categorias, productos y
 * usuarios salen de las mismas listas que alimentan las tablas y se muestran
 * en sus pestañas. Las tarjetas son del dia: mas vendidos y mayor ingreso
 * vienen de `/analytics` con el rango de hoy, y ventas y ticket promedio del
 * resumen del historial de ventas.
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
  // Desde CAL-16 /orders es paginado: en vez de traer todas las ordenes para
  // sumar las de hoy, se pide solo el resumen del dia (una fila basta).
  const today = todayIsoDate();
  const todaySalesQuery = useQuery({
    queryKey: queryKeys.sales.history(today, today, 0, 1),
    queryFn: () =>
      api.get<SalesHistoryResponse>(endpoints.sales.history(), { from: today, to: today, size: 1 }),
  });
  const usersQuery = useQuery({
    queryKey: queryKeys.users.all(),
    queryFn: () => api.get<UserResponse[]>(endpoints.users.list()),
  });
  /** Analitica de hoy (dia del negocio): mas vendidos por unidades y por ingreso. */
  const todayAnalyticsQuery = useQuery({
    queryKey: queryKeys.analytics.summary(today, today),
    queryFn: () =>
      api.get<AnalyticsResponse>(endpoints.analytics.summary(), { from: today, to: today }),
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

  const todaySummary = todaySalesQuery.data?.summary;

  return (
    <ScreenShell
      title="Administración"
      subtitle="Resumen del restaurante; alta de mesas, categorías, productos y usuarios, y su zona horaria."
      actions={
        <ScreenMetric
          label="Cobrado hoy"
          value={todaySalesQuery.isLoading ? "—" : <Money amount={todaySummary?.total ?? 0} />}
          accent
        />
      }
      contentClassName="flex flex-col gap-6"
    >
      <TodayInsights
        analytics={todayAnalyticsQuery.data}
        analyticsLoading={todayAnalyticsQuery.isLoading}
        analyticsFailed={todayAnalyticsQuery.isError}
        salesCount={todaySummary?.count}
        salesTotal={todaySummary?.total}
        salesLoading={todaySalesQuery.isLoading}
      />

      <Tabs defaultValue="tables">
        <TabsList>
          <TabsTrigger value="tables">
            Mesas <TabCount value={tablesQuery.data?.length} />
          </TabsTrigger>
          <TabsTrigger value="categories">
            Categorías <TabCount value={categoriesQuery.data?.length} />
          </TabsTrigger>
          <TabsTrigger value="products">
            Productos <TabCount value={productsQuery.data?.length} />
          </TabsTrigger>
          <TabsTrigger value="users">
            Usuarios <TabCount value={usersQuery.data?.length} />
          </TabsTrigger>
          <TabsTrigger value="restaurant">Restaurante</TabsTrigger>
        </TabsList>

        <TabsContent value="tables" className="flex flex-col gap-4">
          <div className="flex justify-end gap-2">
            <PrintTablesQrDialog tables={tablesQuery.data ?? []} />
            <CreateTableDialog />
          </div>
          <Card>
            <CardContent>
              <QueryState
                isLoading={tablesQuery.isLoading}
                error={tablesQuery.error}
                isEmpty={tablesQuery.data?.length === 0}
                emptyMessage="Todavía no hay mesas. Crea la primera con el botón de arriba."
              >
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>Número</TableHead>
                      <TableHead>Capacidad</TableHead>
                      <TableHead>Ocupación</TableHead>
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
                          <TableCell className="font-mono text-[13px] tabular-nums">
                            {table.number}
                          </TableCell>
                          <TableCell className="font-mono text-[13px] tabular-nums">
                            {table.capacity}
                          </TableCell>
                          <TableCell>
                            <StatusBadge kind="table" status={table.status} />
                          </TableCell>
                          <TableCell>
                            <Badge data-tone={table.active ? "green" : "neutral"}>
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
                                ? "Guardando…"
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
                emptyMessage="Todavía no hay categorías. Crea la primera con el botón de arriba."
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
                        <TableCell className="font-mono text-[13px] tabular-nums">
                          {category.sortOrder}
                        </TableCell>
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
                emptyMessage="Todavía no hay productos. Crea el primero con el botón de arriba."
              >
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>Nombre</TableHead>
                      <TableHead>Descripción</TableHead>
                      <TableHead>Precio</TableHead>
                      <TableHead>Categoría</TableHead>
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
                          <TableCell className="text-muted-foreground">
                            {product.description ?? "—"}
                          </TableCell>
                          <TableCell>
                            <Money amount={product.price} className="font-mono text-[13px]" />
                          </TableCell>
                          <TableCell>
                            {categoriesQuery.data?.find((category) => category.id === product.categoryId)
                              ?.name ?? "Sin categoría"}
                          </TableCell>
                          <TableCell>
                            <Badge data-tone={product.active ? "green" : "neutral"}>
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
                                ? "Guardando…"
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
                emptyMessage="Todavía no hay usuarios. Crea el primero con el botón de arriba."
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
                            <Badge variant="outline">{USER_ROLE_LABELS[user.role]}</Badge>
                          </TableCell>
                          <TableCell>
                            <Badge data-tone={user.active ? "green" : "neutral"}>
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
                                ? "Guardando…"
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

        <TabsContent value="restaurant" className="flex flex-col gap-4">
          <RestaurantSettingsSection />
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
    </ScreenShell>
  );
}

/** Conteo junto al nombre de la pestaña; mientras carga no muestra nada. */
function TabCount({ value }: { value: number | undefined }) {
  if (value === undefined) {
    return null;
  }
  return <span className="font-mono text-[11px] tabular-nums opacity-70">{value}</span>;
}

/** Productos de mayor ingreso que se listan en su tarjeta. */
const TOP_REVENUE_ROWS = 3;

interface TodayInsightsProps {
  analytics: AnalyticsResponse | undefined;
  analyticsLoading: boolean;
  analyticsFailed: boolean;
  salesCount: number | undefined;
  salesTotal: number | undefined;
  salesLoading: boolean;
}

/**
 * Indicadores del dia: el producto mas vendido por unidades, los que mas
 * ingreso generaron, las ventas cobradas y el ticket promedio. Los conteos
 * del catalogo viven en las pestañas; aqui solo va lo que cambia durante el
 * turno.
 */
function TodayInsights({
  analytics,
  analyticsLoading,
  analyticsFailed,
  salesCount,
  salesTotal,
  salesLoading,
}: TodayInsightsProps) {
  const bestSeller = analytics?.topProducts?.[0];
  // "Otros" agrupa la cola del Pareto: no es un producto.
  const topRevenue = (analytics?.pareto ?? [])
    .filter((row) => row.productName !== "Otros")
    .slice(0, TOP_REVENUE_ROWS);
  const count = salesCount ?? 0;
  const total = salesTotal ?? 0;
  const noSalesYet = "Sin ventas cobradas hoy todavía.";
  const analyticsMessage = analyticsLoading
    ? "—"
    : analyticsFailed
      ? "No se pudo cargar la analítica de hoy."
      : noSalesYet;

  return (
    <section aria-label="Indicadores de hoy" className="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-4">
      <Card>
        <CardHeader>
          <CardDescription className="eyebrow">Más vendido hoy</CardDescription>
          {bestSeller ? (
            <>
              <CardTitle className="text-[26px] leading-tight text-balance">{bestSeller.productName}</CardTitle>
              <p className="text-sm text-muted-foreground">
                {bestSeller.quantity} {bestSeller.quantity === 1 ? "unidad" : "unidades"} ·{" "}
                <Money amount={bestSeller.revenue} />
              </p>
            </>
          ) : (
            <p className="text-sm text-muted-foreground">{analyticsMessage}</p>
          )}
        </CardHeader>
      </Card>

      <Card className="md:col-span-2 xl:col-span-1">
        <CardHeader>
          <CardDescription className="eyebrow">Mayor ingreso hoy</CardDescription>
          {topRevenue.length > 0 ? (
            <ol className="mt-1 flex flex-col gap-2">
              {topRevenue.map((row, index) => (
                <li key={row.productName} className="flex items-baseline justify-between gap-3 text-sm">
                  <span className="min-w-0 truncate">
                    <span className="font-mono text-[11px] text-muted-foreground tabular-nums">{index + 1}.</span>{" "}
                    {row.productName}
                  </span>
                  <span className="shrink-0 tabular-nums">
                    <Money amount={row.revenue} />
                    <span className="ml-1.5 text-xs text-muted-foreground">{Math.round(row.revenueShare)}%</span>
                  </span>
                </li>
              ))}
            </ol>
          ) : (
            <p className="text-sm text-muted-foreground">{analyticsMessage}</p>
          )}
        </CardHeader>
      </Card>

      <Card>
        <CardHeader>
          <CardDescription className="eyebrow">Ventas de hoy</CardDescription>
          <CardTitle className="text-[34px] leading-none tabular-nums">{salesLoading ? "—" : count}</CardTitle>
        </CardHeader>
        <CardContent>
          <p className="text-sm text-muted-foreground">
            Cobrado: {salesLoading ? "—" : <Money amount={total} />}
          </p>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardDescription className="eyebrow">Ticket promedio hoy</CardDescription>
          <CardTitle className="text-[34px] leading-none tabular-nums">
            {salesLoading ? "—" : count > 0 ? <Money amount={total / count} /> : "—"}
          </CardTitle>
        </CardHeader>
        <CardContent>
          <p className="text-sm text-muted-foreground">
            {count > 0 ? `Sobre ${count} ${count === 1 ? "venta" : "ventas"}.` : noSalesYet}
          </p>
        </CardContent>
      </Card>
    </section>
  );
}
