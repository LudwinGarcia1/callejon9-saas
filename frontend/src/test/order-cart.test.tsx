// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { ComponentProps } from "react";
import { OrderView } from "@/app/(authenticated)/waiter/order/[id]/order-view";
import type { ProductPicker } from "@/app/(authenticated)/waiter/order/[id]/product-picker";
import { api } from "@/lib/api";
import { queryKeys } from "@/lib/query-keys";
import type { OrderResponse, OrderStatus, UserRole } from "@/lib/types";

const session = vi.hoisted(() => ({ role: "WAITER" as UserRole }));
vi.mock("next/navigation", () => ({ useRouter: () => ({ push: vi.fn() }) }));
vi.mock("sonner", () => ({ toast: { success: vi.fn(), error: vi.fn() } }));
vi.mock("@/hooks/use-session", () => ({
  useSession: () => ({ user: { userId: "u1", fullName: "Mesero de piso", role: session.role }, isLoading: false, logout: vi.fn() }),
}));
vi.mock("@/lib/api", async (original) => ({ ...await original<typeof import("@/lib/api")>(), api: { get: vi.fn(), post: vi.fn(), del: vi.fn() } }));
// El selector real agrupa por categoría; aquí basta con exponer sus acciones sobre un producto.
vi.mock("@/app/(authenticated)/waiter/order/[id]/product-picker", () => ({
  ProductPicker: (props: ComponentProps<typeof ProductPicker>) => <section>
    <button onClick={() => props.onAddProduct({ id: "p-flan", name: "Flan napolitano", price: 75, active: true, categoryId: null, description: null })}>Agregar flan</button>
    <button onClick={() => props.onIncrement(props.cart.find((line) => line.product.id === "p-flan")?.key ?? "")}>Más flan</button>
    <button onClick={() => props.onNotesChange(props.cart[0]?.key ?? "", "  sin crema  ")}>Nota a la primera línea</button>
    <p>Cantidad: {props.cart.find((line) => line.product.id === "p-flan")?.quantity ?? 0}</p>
    <p>Líneas: {props.cart.length}</p>
    <button disabled={props.cart.length === 0} onClick={props.onCommit}>Agregar a la orden</button>
  </section>,
}));

let client: QueryClient;
function orderWith(status: OrderStatus): OrderResponse {
  return { id: "o1", folio: "ORD-261021185004", tableId: null, waiterId: "u1", guestCount: 2, status, total: 85,
    openedAt: "2026-10-21T18:50:04Z", sentToKitchenAt: status === "NEW" ? null : "2026-10-21T18:51:00Z", closedAt: null,
    items: [{ id: "i1", productId: "p-sopa", productName: "Sopa de tortilla", quantity: 1, unitPrice: 85, kitchenStatus: "PENDING", notes: null }] };
}

function mount(status: OrderStatus, role: UserRole = "WAITER") {
  session.role = role;
  const order = orderWith(status);
  client.setQueryData(queryKeys.orders.detail("o1"), order);
  client.setQueryData(queryKeys.tables.all(), []);
  client.setQueryData(queryKeys.categories.all(), []);
  client.setQueryData(queryKeys.products.all(), []);
  vi.mocked(api.get).mockImplementation(async (path: string) => (path.includes("/orders/o1") ? order : []));
  render(<QueryClientProvider client={client}><OrderView orderId="o1" /></QueryClientProvider>);
}

beforeEach(() => {
  vi.resetAllMocks();
  client = new QueryClient({ defaultOptions: { queries: { retry: false, staleTime: Infinity }, mutations: { retry: false } } });
});
afterEach(() => {
  cleanup();
  client.clear();
});

describe("carrito de la orden: tope de 99 por línea", () => {
  it("subir la cantidad se detiene en 99 y el lote enviado lleva 99, no más", async () => {
    vi.mocked(api.post).mockResolvedValue(orderWith("NEW"));
    mount("NEW");
    const add = await screen.findByRole("button", { name: "Agregar flan" });
    const more = () => fireEvent.click(screen.getByRole("button", { name: "Más flan" }));
    fireEvent.click(add);
    for (let i = 0; i < 97; i += 1) more();
    expect(screen.getByText("Cantidad: 98")).toBeTruthy();

    more();
    expect(screen.getByText("Cantidad: 99")).toBeTruthy();
    // Ni el botón «+» de la línea ni volver a agregar el producto pasan del tope.
    more();
    fireEvent.click(add);
    expect(screen.getByText("Cantidad: 99")).toBeTruthy();

    fireEvent.click(screen.getByRole("button", { name: "Agregar a la orden" }));
    await waitFor(() => expect(api.post).toHaveBeenCalledWith("/api/v1/orders/o1/items",
      { items: [{ productId: "p-flan", quantity: 99 }] }));
  }, 20_000);
});

describe("notas por producto (CAL-151)", () => {
  it("la nota viaja recortada con su línea y una unidad más del mismo producto abre otra línea sin nota", async () => {
    vi.mocked(api.post).mockResolvedValue(orderWith("NEW"));
    mount("NEW");
    const add = await screen.findByRole("button", { name: "Agregar flan" });
    fireEvent.click(add);
    fireEvent.click(screen.getByRole("button", { name: "Nota a la primera línea" }));
    fireEvent.click(add);
    expect(screen.getByText("Líneas: 2")).toBeTruthy();

    fireEvent.click(screen.getByRole("button", { name: "Agregar a la orden" }));
    await waitFor(() => expect(api.post).toHaveBeenCalledWith("/api/v1/orders/o1/items", {
      items: [
        { productId: "p-flan", quantity: 1, notes: "sin crema" },
        { productId: "p-flan", quantity: 1 },
      ],
    }));
  });

  it("sin nota, volver a tocar el producto suma a la misma línea", async () => {
    mount("NEW");
    const add = await screen.findByRole("button", { name: "Agregar flan" });
    fireEvent.click(add);
    fireEvent.click(add);
    expect(screen.getByText("Líneas: 1")).toBeTruthy();
    expect(screen.getByText("Cantidad: 2")).toBeTruthy();
  });
});

describe("quitar un producto ya agregado (CAL-151)", () => {
  it("con la orden en Nueva quita la línea y muestra el total que devuelve la API", async () => {
    const emptied = { ...orderWith("NEW"), total: 0, items: [] };
    vi.mocked(api.del).mockResolvedValue(emptied);
    mount("NEW");
    // Tras quitar, la API ya devuelve la orden sin la línea; el refetch que
    // dispara la invalidación debe ver lo mismo.
    vi.mocked(api.get).mockImplementation(async (path: string) => (path.includes("/orders/o1") ? emptied : []));
    fireEvent.click(await screen.findByRole("button", { name: "Quitar Sopa de tortilla de la orden" }));
    await waitFor(() => expect(api.del).toHaveBeenCalledWith("/api/v1/orders/o1/items/i1"));
    await screen.findByText("Comanda vacía");
  });

  it.each<OrderStatus>(["SENT", "READY", "PAID", "CANCELED"])("con la orden %s no se ofrece quitar", async (status) => {
    mount(status, "ADMIN");
    await screen.findByText("ORD-261021185004", { exact: false });
    expect(screen.queryByRole("button", { name: "Quitar Sopa de tortilla de la orden" })).toBeNull();
  });
});

describe("cancelación según el rol (CAL-113)", () => {
  const cases: Array<[UserRole, OrderStatus, boolean]> = [
    ["WAITER", "NEW", true],
    ["WAITER", "SENT", false],
    ["WAITER", "READY", false],
    ["ADMIN", "NEW", true],
    ["ADMIN", "SENT", true],
    ["ADMIN", "READY", true],
    ["ADMIN", "PAID", false],
    ["ADMIN", "CANCELED", false],
  ];

  it.each(cases)("%s con orden %s: botón «Cancelar orden» visible = %s", async (role, status, visible) => {
    mount(status, role);
    await screen.findByText("ORD-261021185004", { exact: false });
    expect(screen.queryByRole("button", { name: "Cancelar orden" }) !== null).toBe(visible);
  });
});
