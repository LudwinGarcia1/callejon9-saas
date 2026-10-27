// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { afterEach, beforeEach, expect, it, vi } from "vitest";

import { EditProductDialog } from "@/app/(authenticated)/admin/edit-product-dialog";
import { ProductPicker } from "@/app/(authenticated)/waiter/order/[id]/product-picker";
import { api } from "@/lib/api";
import { formatCurrency } from "@/lib/format";
import { queryKeys } from "@/lib/query-keys";
import type { CategoryResponse, ProductResponse } from "@/lib/types";

const effects = vi.hoisted(() => ({ success: vi.fn(), error: vi.fn() }));
vi.mock("sonner", () => ({ toast: effects }));
vi.mock("@/lib/api", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/lib/api")>();
  return { ...actual, api: { ...actual.api, put: vi.fn() } };
});

const category: CategoryResponse = { id: "cat-1", name: "Especiales", sortOrder: 0 };
const product: ProductResponse = {
  id: "product-1",
  name: "Taco de setas",
  description: "Con salsa verde",
  price: 42.5,
  categoryId: category.id,
  active: true,
};
const products: ProductResponse[] = [
  product,
  { ...product, id: "product-2", name: "Agua fresca", price: 28 },
  { ...product, id: "product-3", name: "Flan", price: 35 },
];

let client: QueryClient;

beforeEach(() => {
  vi.resetAllMocks();
  client = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
});

afterEach(() => {
  cleanup();
  client.clear();
});

it("muestra los productos activos y sus precios en el selector del mesero", () => {
  const onAddProduct = vi.fn();
  render(
    <ProductPicker
      categories={[category]}
      products={products}
      isLoading={false}
      cart={[]}
      onAddProduct={onAddProduct}
      onIncrement={vi.fn()}
      onDecrement={vi.fn()}
      onRemove={vi.fn()}
      onCommit={vi.fn()}
      isCommitting={false}
      disabled={false}
      alreadySentToKitchen={false}
    />,
  );

  for (const item of products) {
    expect(screen.getByText(item.name)).toBeTruthy();
    expect(screen.getByText(formatCurrency(item.price))).toBeTruthy();
  }

  fireEvent.click(screen.getByRole("button", { name: "Agregar Taco de setas" }));
  expect(onAddProduct).toHaveBeenCalledWith(product);
});

it("permite al administrador corregir el precio y actualiza el catálogo", async () => {
  const onOpenChange = vi.fn();
  const updatedProduct = { ...product, price: 47.5 };
  vi.mocked(api.put).mockResolvedValue(updatedProduct);
  client.setQueryData(queryKeys.products.all(true), [product]);

  render(
    <QueryClientProvider client={client}>
      <EditProductDialog product={product} categories={[category]} onOpenChange={onOpenChange} />
    </QueryClientProvider>,
  );

  fireEvent.change(screen.getByLabelText("Precio"), { target: { value: "47.5" } });
  fireEvent.click(screen.getByRole("button", { name: "Guardar cambios" }));

  await waitFor(() => {
    expect(api.put).toHaveBeenCalledWith("/api/v1/products/product-1", {
      name: "Taco de setas",
      description: "Con salsa verde",
      price: 47.5,
      categoryId: category.id,
    });
  });
  await waitFor(() => expect(onOpenChange).toHaveBeenCalledWith(false));
  expect(effects.success).toHaveBeenCalledWith('Producto "Taco de setas" actualizado.');
  expect(client.getQueryState(queryKeys.products.all(true))?.isInvalidated).toBe(true);
});
