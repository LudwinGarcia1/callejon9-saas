// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { afterEach, expect, it, vi } from "vitest";

import { CreateTableDialog } from "@/app/(authenticated)/admin/create-table-dialog";
import { WaiterView } from "@/app/(authenticated)/waiter/waiter-view";
import { api } from "@/lib/api";
import { endpoints } from "@/lib/endpoints";
import type { OrderResponse, TableResponse } from "@/lib/types";

const { push } = vi.hoisted(() => ({ push: vi.fn() }));
vi.mock("next/navigation", () => ({ useRouter: () => ({ push }) }));
vi.mock("sonner", () => ({ toast: { success: vi.fn(), error: vi.fn() } }));
vi.mock("@/lib/api", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/lib/api")>();
  return { ...actual, api: { ...actual.api, get: vi.fn(), post: vi.fn() } };
});

afterEach(() => {
  cleanup();
  vi.resetAllMocks();
});

function mountSession(view: React.ReactNode) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false, staleTime: Infinity } },
  });
  const session = render(<QueryClientProvider client={client}>{view}</QueryClientProvider>);
  return () => {
    session.unmount();
    client.clear();
  };
}

function tableCard(number: number) {
  return screen.getByRole("button", { name: new RegExp(`^Mesa ${number} `) });
}

it("conserva número y capacidad de las tres mesas y actualiza solo la que ocupa el mesero", async () => {
  const registrations = [
    { number: 11, capacity: 2 },
    { number: 12, capacity: 4 },
    { number: 13, capacity: 6 },
  ];
  let tables: TableResponse[] = [];
  const order: OrderResponse = {
    id: "order-12",
    folio: "F-012",
    tableId: "table-12",
    waiterId: "waiter-1",
    guestCount: 2,
    status: "NEW",
    total: 0,
    openedAt: "2026-11-24T18:00:00Z",
    items: [],
    sentToKitchenAt: null,
    closedAt: null,
  };
  let orders: OrderResponse[] = [];

  vi.mocked(api.get).mockImplementation(async (path) => {
    if (path === endpoints.tables.list()) return tables;
    if (path === endpoints.orders.list()) return { content: orders, totalElements: orders.length };
    throw new Error(`Unexpected GET: ${path}`);
  });
  vi.mocked(api.post).mockImplementation(async (path, request) => {
    if (path === endpoints.tables.create()) {
      const { number, capacity } = request as (typeof registrations)[number];
      const table: TableResponse = {
        id: `table-${number}`, number, capacity, status: "FREE", waiterId: null, active: true,
      };
      tables = [...tables, table];
      return table;
    }
    if (path === endpoints.orders.open()) {
      expect(request).toEqual({ tableId: order.tableId, guestCount: order.guestCount });
      tables = tables.map((table) =>
        table.id === order.tableId
          ? { ...table, status: "OCCUPIED", waiterId: order.waiterId }
          : table,
      );
      orders = [order];
      return order;
    }
    throw new Error(`Unexpected POST: ${path}`);
  });

  const closeAdmin = mountSession(<CreateTableDialog />);
  for (const { number, capacity } of registrations) {
    fireEvent.click(screen.getByRole("button", { name: "Nueva mesa" }));
    fireEvent.change(screen.getByLabelText("Número"), { target: { value: String(number) } });
    fireEvent.change(screen.getByLabelText("Capacidad"), { target: { value: String(capacity) } });
    fireEvent.click(screen.getByRole("button", { name: "Crear mesa" }));
    await waitFor(() => expect(screen.queryByRole("dialog")).toBeNull());
    expect(api.post).toHaveBeenLastCalledWith(endpoints.tables.create(), { number, capacity });
  }
  closeAdmin();

  const closeWaiter = mountSession(<WaiterView />);
  try {
    await screen.findByText("4 lugares");
    for (const { number, capacity } of registrations) {
      const card = within(tableCard(number));
      expect(card.getByText(`${capacity} lugares`)).toBeTruthy();
      expect(card.getByText("Libre")).toBeTruthy();
    }

    fireEvent.click(within(tableCard(12)).getByRole("button", { name: "Abrir mesa" }));
    const dialog = await screen.findByRole("dialog", { name: "Abrir mesa 12" });
    fireEvent.click(within(dialog).getByRole("button", { name: "Abrir orden" }));

    await waitFor(() => expect(within(tableCard(12)).getByText("Ocupada")).toBeTruthy());
    expect(push).toHaveBeenCalledWith("/waiter/order/order-12");
    expect(within(tableCard(11)).getByText("Libre")).toBeTruthy();
    expect(within(tableCard(13)).getByText("Libre")).toBeTruthy();
  } finally {
    closeWaiter();
  }
});
