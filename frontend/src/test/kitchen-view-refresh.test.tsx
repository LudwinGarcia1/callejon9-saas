// @vitest-environment jsdom
import { cleanup, render, screen } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { afterEach, beforeEach, expect, it, vi } from "vitest";

import { KitchenView } from "@/app/(authenticated)/kitchen/kitchen-view";
import { api } from "@/lib/api";
import { KITCHEN_SYNC_INTERVAL_MS } from "@/lib/kitchen-timing";
import { queryKeys } from "@/lib/query-keys";
import type { KitchenOrderResponse } from "@/lib/types";

vi.mock("@/lib/api", async (original) => ({
  ...await original<typeof import("@/lib/api")>(),
  api: { get: vi.fn() },
}));

let now: Date;

function kitchenOrder(id: string, folio: string, sentAt: string): KitchenOrderResponse {
  return {
    id,
    folio,
    tableId: null,
    status: "SENT",
    sentToKitchenAt: sentAt,
    items: [
      {
        id: `${id}-item`,
        orderId: id,
        productId: "product-1",
        productName: "Tacos de prueba",
        quantity: 1,
        kitchenStatus: "PENDING",
        notes: null,
      },
    ],
  };
}

let client: QueryClient;
let orders: KitchenOrderResponse[];

beforeEach(() => {
  now = new Date();
  vi.resetAllMocks();

  const older = kitchenOrder(
    "order-older",
    "ORD-OLDER",
    new Date(now.getTime() - 6 * 60_000).toISOString(),
  );
  orders = [older];
  client = new QueryClient({
    defaultOptions: { queries: { retry: false, staleTime: Infinity } },
  });
  client.setQueryData(queryKeys.kitchen.orders(), orders);
  client.setQueryData(queryKeys.tables.all(), []);

  vi.mocked(api.get).mockImplementation(async (path) =>
    path === "/api/v1/kitchen/orders" ? orders : [],
  );
});

afterEach(() => {
  cleanup();
  client.clear();
});

it("adds incoming orders on the next poll and keeps the oldest order first", async () => {
  const { container } = render(
    <QueryClientProvider client={client}>
      <KitchenView />
    </QueryClientProvider>,
  );

  expect(screen.getByText("hace 6 min")).toBeTruthy();
  expect(api.get).not.toHaveBeenCalled();

  const newer = kitchenOrder(
    "order-newer",
    "ORD-NEWER",
    new Date(now.getTime() - 20_000).toISOString(),
  );
  orders = [orders[0], newer];

  expect(
    await screen.findByText("ORD-NEWER", {}, { timeout: KITCHEN_SYNC_INTERVAL_MS + 2_000 }),
  ).toBeTruthy();
  const cards = Array.from(container.querySelectorAll("article"));
  expect(api.get).toHaveBeenCalledWith("/api/v1/kitchen/orders");
  expect(cards).toHaveLength(2);
  expect(cards[0].textContent).toContain("ORD-OLDER");
  expect(cards[1].textContent).toContain("ORD-NEWER");
  expect(screen.getByText("hace 6 min")).toBeTruthy();
  expect(screen.getByText("recién enviada")).toBeTruthy();
}, KITCHEN_SYNC_INTERVAL_MS + 5_000);
