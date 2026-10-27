// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { afterEach, beforeEach, expect, it, vi } from "vitest";

import { CreateTableDialog } from "@/app/(authenticated)/admin/create-table-dialog";
import { WaiterView } from "@/app/(authenticated)/waiter/waiter-view";
import { api } from "@/lib/api";
import type { OrderResponse, OrderSummaryResponse, TableResponse } from "@/lib/types";

const effects = vi.hoisted(() => ({
  push: vi.fn(),
  success: vi.fn(),
  error: vi.fn(),
}));
vi.mock("next/navigation", () => ({ useRouter: () => ({ push: effects.push }) }));
vi.mock("sonner", () => ({ toast: effects }));
vi.mock("@/lib/api", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/lib/api")>();
  return { ...actual, api: { ...actual.api, get: vi.fn(), post: vi.fn() } };
});

const initialTables: TableResponse[] = [
  { id: "table-1", number: 1, capacity: 2, status: "FREE", waiterId: null, active: true },
  { id: "table-2", number: 2, capacity: 4, status: "OCCUPIED", waiterId: "waiter-1", active: true },
  { id: "table-3", number: 3, capacity: 6, status: "FREE", waiterId: null, active: true },
];
const openOrder: OrderSummaryResponse = {
  id: "order-2",
  folio: "F-002",
  tableId: "table-2",
  waiterId: "waiter-1",
  guestCount: 3,
  status: "SENT",
  total: 120,
  openedAt: "2026-10-20T18:00:00Z",
};

let client: QueryClient;
let tables: TableResponse[];

beforeEach(() => {
  vi.resetAllMocks();
  client = new QueryClient({
    defaultOptions: { queries: { retry: false, staleTime: Infinity }, mutations: { retry: false } },
  });
  tables = [...initialTables];
  vi.mocked(api.get).mockImplementation(async (path) =>
    path === "/api/v1/tables" ? tables : path === "/api/v1/orders" ? [openOrder] : [],
  );
});

afterEach(() => {
  cleanup();
  client.clear();
});

function mountWaiter() {
  return render(
    <QueryClientProvider client={client}>
      <WaiterView />
    </QueryClientProvider>,
  );
}

it("muestra número, capacidad y estado de cada mesa de un vistazo", async () => {
  mountWaiter();

  await screen.findByText("2 lugares");
  expect(screen.getByText("1")).toBeTruthy();
  expect(screen.getByText("2")).toBeTruthy();
  expect(screen.getByText("3")).toBeTruthy();
  expect(screen.getByText("4 lugares")).toBeTruthy();
  expect(screen.getByText("6 lugares")).toBeTruthy();
  expect(screen.getAllByText("Lista para sentar")).toHaveLength(2);

  const freeCard = screen.getAllByText("Lista para sentar")[0].closest("[data-tone]");
  const occupiedCard = screen.getByText("Ocupada").closest("[data-tone]");
  expect(freeCard?.getAttribute("data-tone")).toBe("green");
  expect(occupiedCard?.getAttribute("data-tone")).toBe("brand");
});

it("abre la mesa libre y refresca su estado cuando el mesero ocupa una", async () => {
  const openedOrder: OrderResponse = {
    ...openOrder,
    id: "order-1",
    folio: "F-001",
    tableId: "table-1",
    guestCount: 2,
    status: "NEW",
    total: 0,
    items: [],
    sentToKitchenAt: null,
    closedAt: null,
  };
  vi.mocked(api.post).mockImplementation(async () => {
    tables = tables.map((table) =>
      table.id === "table-1" ? { ...table, status: "OCCUPIED" } : table,
    );
    return openedOrder;
  });

  mountWaiter();
  await screen.findByText("2 lugares");
  const openTableAction = screen
    .getAllByRole("button")
    .find((button) => button.textContent?.trim() === "Abrir mesa");
  expect(openTableAction).toBeTruthy();
  fireEvent.click(openTableAction!);
  expect(await screen.findByText("Abrir mesa 1")).toBeTruthy();
  fireEvent.click(screen.getByRole("button", { name: "Abrir orden" }));

  await waitFor(() => {
    expect(api.post).toHaveBeenCalledWith("/api/v1/orders", {
      tableId: "table-1",
      guestCount: 2,
    });
  });
  await waitFor(() => expect(effects.push).toHaveBeenCalledWith("/waiter/order/order-1"));
  await waitFor(() => expect(screen.getAllByText("Ocupada")).toHaveLength(2));
  expect(api.get).toHaveBeenCalledTimes(4);
});

it("permite al administrador crear una mesa con su capacidad", async () => {
  const created: TableResponse = {
    id: "table-4",
    number: 4,
    capacity: 8,
    status: "FREE",
    waiterId: null,
    active: true,
  };
  vi.mocked(api.post).mockResolvedValue(created);

  render(
    <QueryClientProvider client={client}>
      <CreateTableDialog />
    </QueryClientProvider>,
  );

  fireEvent.click(screen.getByRole("button", { name: "Nueva mesa" }));
  fireEvent.change(screen.getByLabelText("Número"), { target: { value: "4" } });
  fireEvent.change(screen.getByLabelText("Capacidad"), { target: { value: "8" } });
  fireEvent.click(screen.getByRole("button", { name: "Crear mesa" }));

  await waitFor(() => expect(api.post).toHaveBeenCalledWith("/api/v1/tables", {
    number: 4,
    capacity: 8,
  }));
  expect(effects.success).toHaveBeenCalledWith("Mesa 4 creada.");
});
