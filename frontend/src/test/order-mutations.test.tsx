// @vitest-environment jsdom
import { act, cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { OrderView } from "@/app/(authenticated)/waiter/order/[id]/order-view";
import { api, ApiError } from "@/lib/api";
import { queryKeys } from "@/lib/query-keys";
import type { OrderResponse } from "@/lib/types";
import type { ComponentProps } from "react";
import type { ProductPicker } from "@/app/(authenticated)/waiter/order/[id]/product-picker";

const effects = vi.hoisted(() => ({ push: vi.fn(), success: vi.fn(), error: vi.fn() }));
vi.mock("next/navigation", () => ({ useRouter: () => ({ push: effects.push }) }));
vi.mock("sonner", () => ({ toast: effects }));
vi.mock("@/lib/api", async (original) => ({ ...await original<typeof import("@/lib/api")>(), api: { get: vi.fn(), post: vi.fn() } }));
vi.mock("@/app/(authenticated)/waiter/order/[id]/product-picker", () => ({
  ProductPicker: (props: ComponentProps<typeof ProductPicker>) => <section>
    <button onClick={() => props.onAddProduct({ id: "p1", name: "Producto", price: 10, active: true, categoryId: null, description: null })}>Agregar</button>
    <p>Carrito: {props.cart.length}</p>
    <button disabled={props.isCommitting || props.cart.length === 0} onClick={props.onCommit}>{props.isCommitting ? "Guardando" : "Guardar"}</button>
  </section>,
}));

let client: QueryClient;
let order: OrderResponse;
beforeEach(() => {
  vi.resetAllMocks();
  client = new QueryClient({ defaultOptions: { queries: { retry: false, staleTime: Infinity }, mutations: { retry: false } } });
  order = { id: "o1", folio: "O-1", tableId: null, waiterId: null, guestCount: 1, status: "NEW", total: 10,
    openedAt: "2026-10-08T12:00:00Z", sentToKitchenAt: null, closedAt: null,
    items: [{ id: "i1", productId: "p1", productName: "Producto", quantity: 1, unitPrice: 10, kitchenStatus: "PENDING", notes: null }] };
  client.setQueryData(queryKeys.orders.detail("o1"), order);
  client.setQueryData(queryKeys.orders.all(), [order]);
  client.setQueryData(queryKeys.tables.all(), []);
  client.setQueryData(queryKeys.categories.all(), []);
  client.setQueryData(queryKeys.products.all(), []);
  vi.mocked(api.get).mockImplementation(async (path) => path.includes("/orders/o1") ? order : []);
});
afterEach(() => { cleanup(); client.clear(); });
function mount() { render(<QueryClientProvider client={client}><OrderView orderId="o1" /></QueryClientProvider>); }
function deferred() {
  let resolve!: (value: OrderResponse) => void;
  let reject!: (error: Error) => void;
  const promise = new Promise<OrderResponse>((yes, no) => { resolve = yes; reject = no; });
  vi.mocked(api.post).mockReturnValue(promise);
  return { resolve, reject };
}

it("blocks a second send while pending and invalidates only order data after success", async () => {
  const pending = deferred(); mount();
  fireEvent.click(screen.getByRole("button", { name: "Enviar a cocina" }));
  const button = await screen.findByRole("button", { name: "Enviando…" });
  expect((button as HTMLButtonElement).disabled).toBe(true);
  fireEvent.click(button);
  await waitFor(() => expect(api.post).toHaveBeenCalledTimes(1));
  expect(api.post).toHaveBeenCalledWith("/api/v1/orders/o1/send-to-kitchen");
  order = { ...order, status: "SENT" };
  await act(async () => pending.resolve(order));
  await screen.findByText("Ya está en cocina");
  expect(client.getQueryData(queryKeys.orders.detail("o1"))).toEqual(order);
  expect(client.getQueryState(queryKeys.orders.all())?.isInvalidated).toBe(true);
  expect(client.getQueryState(queryKeys.tables.all())?.isInvalidated).toBe(false);
});
it("preserves RFC7807 detail and restores sending after a rejected mutation", async () => {
  const pending = deferred(); mount();
  fireEvent.click(screen.getByRole("button", { name: "Enviar a cocina" }));
  await screen.findByRole("button", { name: "Enviando…" });
  await act(async () => pending.reject(new ApiError({ detail: "Orden modificada" }, 409)));
  await waitFor(() => expect(effects.error).toHaveBeenCalledWith("Orden modificada"));
  expect((screen.getByRole("button", { name: "Enviar a cocina" }) as HTMLButtonElement).disabled).toBe(false);
  expect(client.getQueryState(queryKeys.orders.all())?.isInvalidated).toBe(false);
});
it.each([false, true])("preserves cart on failure and clears it on success (failure=%s)", async (failure) => {
  const pending = deferred(); mount();
  fireEvent.click(screen.getByRole("button", { name: "Agregar" }));
  fireEvent.click(screen.getByRole("button", { name: "Guardar" }));
  const button = await screen.findByRole("button", { name: "Guardando" });
  fireEvent.click(button);
  await waitFor(() => expect(api.post).toHaveBeenCalledTimes(1));
  expect(api.post).toHaveBeenCalledWith("/api/v1/orders/o1/items", { items: [{ productId: "p1", quantity: 1 }] });
  await act(async () => failure ? pending.reject(new Error("offline")) : pending.resolve(order));
  await screen.findByText(`Carrito: ${failure ? 1 : 0}`);
  if (failure) expect(effects.error).toHaveBeenCalledWith("No se pudieron agregar los productos.");
  else expect(effects.success).toHaveBeenCalledWith("Productos agregados a la orden.");
});
it("cancels only once, invalidates orders and tables, and returns to waiter", async () => {
  const pending = deferred(); mount();
  fireEvent.click(screen.getByRole("button", { name: "Cancelar orden" }));
  fireEvent.click(await screen.findByRole("button", { name: "Sí, cancelar orden" }));
  const button = await screen.findByRole("button", { name: "Cancelando…" });
  fireEvent.click(button);
  await waitFor(() => expect(api.post).toHaveBeenCalledTimes(1));
  await act(async () => pending.resolve({ ...order, status: "CANCELED" }));
  await waitFor(() => expect(effects.push).toHaveBeenCalledWith("/waiter"));
  expect(client.getQueryState(queryKeys.orders.all())?.isInvalidated).toBe(true);
});
