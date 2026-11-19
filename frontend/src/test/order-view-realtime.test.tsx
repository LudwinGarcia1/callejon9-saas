// @vitest-environment jsdom
import { act, cleanup, render, screen, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { OrderView } from "@/app/(authenticated)/waiter/order/[id]/order-view";
import { api } from "@/lib/api";
import { queryKeys } from "@/lib/query-keys";
import type { KitchenChannelEvent } from "@/lib/realtime";
import type { OrderResponse, SessionResponse } from "@/lib/types";

const channel = vi.hoisted(() => ({
  tenantId: null as string | null,
  onEvent: null as null | ((event: KitchenChannelEvent) => void),
}));
vi.mock("next/navigation", () => ({ useRouter: () => ({ push: vi.fn() }) }));
vi.mock("sonner", () => ({ toast: { success: vi.fn(), error: vi.fn() } }));
vi.mock("@/lib/api", async (original) => ({ ...await original<typeof import("@/lib/api")>(), api: { get: vi.fn(), post: vi.fn() } }));
vi.mock("@/app/(authenticated)/waiter/order/[id]/product-picker", () => ({ ProductPicker: () => null }));
vi.mock("@/lib/realtime", async (original) => ({
  ...await original<typeof import("@/lib/realtime")>(),
  connectKitchenChannel: vi.fn((options: { tenantId: string; onEvent: (event: KitchenChannelEvent) => void }) => {
    channel.tenantId = options.tenantId;
    channel.onEvent = options.onEvent;
    return () => {};
  }),
}));

const session = { userId: "u1", fullName: "Mesero", role: "WAITER", tenantId: "t1", slug: "pruebas",
  restaurantName: "Pruebas", timezone: "America/Mexico_City" } satisfies SessionResponse;
let client: QueryClient;
let order: OrderResponse;

beforeEach(() => {
  vi.clearAllMocks();
  channel.tenantId = null;
  channel.onEvent = null;
  client = new QueryClient({ defaultOptions: { queries: { retry: false, staleTime: Infinity } } });
  order = { id: "o1", folio: "O-1", tableId: null, waiterId: "u1", guestCount: 2, status: "SENT", total: 30,
    openedAt: "2026-10-30T12:00:00Z", sentToKitchenAt: "2026-10-30T12:05:00Z", closedAt: null,
    items: [
      { id: "i1", productId: "p1", productName: "Sopa", quantity: 1, unitPrice: 10, kitchenStatus: "IN_PREPARATION", notes: null },
      { id: "i2", productId: "p2", productName: "Agua", quantity: 2, unitPrice: 10, kitchenStatus: "PENDING", notes: null },
    ] };
  client.setQueryData(queryKeys.session.me(), session);
  client.setQueryData(queryKeys.orders.detail("o1"), order);
  for (const key of [queryKeys.tables.all(), queryKeys.categories.all(), queryKeys.products.all()]) {
    client.setQueryData(key, []);
  }
  vi.mocked(api.get).mockImplementation(async (path) => path.includes("/orders/o1") ? order : []);
});
afterEach(() => { cleanup(); client.clear(); });

function mount() {
  render(<QueryClientProvider client={client}><OrderView orderId="o1" /></QueryClientProvider>);
}

it("shows the kitchen status of each product once the order is in the kitchen", () => {
  mount();
  expect(screen.getByText("En preparación")).toBeTruthy();
  expect(screen.getByText("Pendiente")).toBeTruthy();
});

it("hides the kitchen status before the order is sent", () => {
  order = { ...order, status: "NEW", items: order.items.map((line) => ({ ...line, kitchenStatus: "PENDING" })) };
  client.setQueryData(queryKeys.orders.detail("o1"), order);
  mount();
  expect(screen.queryByText("Pendiente")).toBeNull();
});

it("listens on the restaurant of the session", () => {
  mount();
  expect(channel.tenantId).toBe("t1");
});

it("refetches the order when kitchen moves one of its products, without a reload", async () => {
  mount();
  order = { ...order, status: "READY", items: order.items.map((line) => ({ ...line, kitchenStatus: "READY" })) };
  await act(async () => channel.onEvent?.({ id: "i1", orderId: "o1", productId: "p1", productName: "Sopa",
    quantity: 1, kitchenStatus: "READY", notes: null }));
  await waitFor(() => expect(screen.getAllByText("Listo")).toHaveLength(2));
  expect(screen.getByText("Lista")).toBeTruthy();
});

it("ignores events of other orders", async () => {
  mount();
  vi.mocked(api.get).mockClear();
  await act(async () => channel.onEvent?.({ id: "i9", orderId: "o9", productId: "p1", productName: "Sopa",
    quantity: 1, kitchenStatus: "READY", notes: null }));
  expect(client.getQueryState(queryKeys.orders.detail("o1"))?.isInvalidated).toBe(false);
  expect(api.get).not.toHaveBeenCalledWith("/api/v1/orders/o1");
});
