"use client";

import { useMemo } from "react";
import { useQuery } from "@tanstack/react-query";

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
import { api } from "@/lib/api";
import { endpoints } from "@/lib/endpoints";
import { queryKeys } from "@/lib/query-keys";
import type {
  CategoryResponse,
  OrderSummaryResponse,
  ProductResponse,
  TableResponse,
} from "@/lib/types";
import { CreateCategoryDialog } from "./create-category-dialog";
import { CreateProductDialog } from "./create-product-dialog";
import { CreateTableDialog } from "./create-table-dialog";

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
  const tablesQuery = useQuery({
    queryKey: queryKeys.tables.all(),
    queryFn: () => api.get<TableResponse[]>(endpoints.tables.list()),
  });
  const categoriesQuery = useQuery({
    queryKey: queryKeys.categories.all(),
    queryFn: () => api.get<CategoryResponse[]>(endpoints.categories.list()),
  });
  const productsQuery = useQuery({
    queryKey: queryKeys.products.all(),
    queryFn: () => api.get<ProductResponse[]>(endpoints.products.list()),
  });
  const ordersQuery = useQuery({
    queryKey: queryKeys.orders.all(),
    queryFn: () => api.get<OrderSummaryResponse[]>(endpoints.orders.list()),
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
          Resumen del restaurante, y alta de mesas, categorias y productos.
        </p>
      </div>

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
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
                      <TableHead>Estado</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {tablesQuery.data?.map((table) => (
                      <TableRow key={table.id}>
                        <TableCell>{table.number}</TableCell>
                        <TableCell>{table.capacity}</TableCell>
                        <TableCell>
                          <StatusBadge kind="table" status={table.status} />
                        </TableCell>
                      </TableRow>
                    ))}
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
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {categoriesQuery.data?.map((category) => (
                      <TableRow key={category.id}>
                        <TableCell>{category.name}</TableCell>
                        <TableCell>{category.sortOrder}</TableCell>
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
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {productsQuery.data?.map((product) => (
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
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </QueryState>
            </CardContent>
          </Card>
        </TabsContent>
      </Tabs>
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
