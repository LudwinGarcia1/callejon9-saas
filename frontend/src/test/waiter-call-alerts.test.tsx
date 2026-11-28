// @vitest-environment jsdom
import { act, cleanup, fireEvent, render, screen } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { toast } from "sonner";
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import WaiterLayout from "@/app/(authenticated)/waiter/layout";
import { queryKeys } from "@/lib/query-keys";
import type { SessionResponse, WaiterCallResponse } from "@/lib/types";

const channel = vi.hoisted(() => ({
  tenantId: null as string | null,
  onEvent: null as null | ((call: WaiterCallResponse) => void),
}));
vi.mock("next/navigation", () => ({ useRouter: () => ({ push: vi.fn(), replace: vi.fn() }) }));
vi.mock("sonner", () => ({ toast: { warning: vi.fn(), dismiss: vi.fn() } }));
vi.mock("@/lib/realtime", async (original) => ({
  ...await original<typeof import("@/lib/realtime")>(),
  connectWaiterCallChannel: vi.fn((options: { tenantId: string; onEvent: (call: WaiterCallResponse) => void }) => {
    channel.tenantId = options.tenantId;
    channel.onEvent = options.onEvent;
    return () => {};
  }),
}));

const session = { userId: "u1", fullName: "Mesero", role: "WAITER", tenantId: "t1", slug: "pruebas",
  restaurantName: "Pruebas", timezone: "America/Mexico_City" } satisfies SessionResponse;
let client: QueryClient;

beforeEach(() => {
  vi.clearAllMocks();
  channel.tenantId = null;
  channel.onEvent = null;
  client = new QueryClient({ defaultOptions: { queries: { retry: false, staleTime: Infinity } } });
  client.setQueryData(queryKeys.session.me(), session);
});
afterEach(() => { cleanup(); client.clear(); });

function mount() {
  render(<QueryClientProvider client={client}><WaiterLayout><p>Mesas</p></WaiterLayout></QueryClientProvider>);
}

const call = (tableId: string, tableNumber: number, calledAt = "2026-11-28T20:00:00Z"): WaiterCallResponse =>
  ({ tableId, tableNumber, calledAt });

it("listens on the waiter calls of the restaurant of the session", () => {
  mount();
  expect(channel.tenantId).toBe("t1");
  expect(screen.getByText("Mesas")).toBeTruthy();
});

it("shows a notification and keeps the table in the strip until it is attended", async () => {
  mount();
  await act(async () => channel.onEvent?.(call("m7", 7)));

  expect(toast.warning).toHaveBeenCalledWith("Mesa 7 llama al mesero", expect.objectContaining({ id: "waiter-call-m7" }));
  expect(screen.getByText("Mesa 7")).toBeTruthy();

  fireEvent.click(screen.getByRole("button", { name: "Marcar como atendida la mesa 7" }));
  expect(screen.queryByText("Mesa 7")).toBeNull();
  expect(toast.dismiss).toHaveBeenCalledWith("waiter-call-m7");
});

it("lists one entry per table, the latest call first", async () => {
  mount();
  await act(async () => channel.onEvent?.(call("m7", 7, "2026-11-28T20:00:00Z")));
  await act(async () => channel.onEvent?.(call("m3", 3, "2026-11-28T20:01:00Z")));
  await act(async () => channel.onEvent?.(call("m7", 7, "2026-11-28T20:02:00Z")));

  const entries = screen.getAllByRole("listitem").map((entry) => entry.textContent);
  expect(entries).toHaveLength(2);
  expect(entries[0]).toContain("Mesa 7");
  expect(entries[1]).toContain("Mesa 3");
});
