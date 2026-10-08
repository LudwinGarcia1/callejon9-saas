// @vitest-environment jsdom
import { act, cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { CheckoutPanel } from "@/app/(authenticated)/cashier/checkout-panel";
import { api, ApiError } from "@/lib/api";
import { queryKeys } from "@/lib/query-keys";
import type { OrderResponse, TicketResponse } from "@/lib/types";

vi.mock("sonner", () => ({ toast: { success: vi.fn(), error: vi.fn() } }));
vi.mock("@/lib/api", async (original) => ({ ...await original<typeof import("@/lib/api")>(), api: { get: vi.fn(), post: vi.fn() } }));
let client: QueryClient;
const order: OrderResponse = { id: "o1", folio: "O-1", tableId: null, waiterId: null, guestCount: 2,
  status: "READY", total: 500, openedAt: "2026-10-08T12:00:00Z", sentToKitchenAt: null, closedAt: null, items: [] };
beforeEach(() => {
  vi.resetAllMocks();
  client = new QueryClient({ defaultOptions: { queries: { retry: false, staleTime: Infinity }, mutations: { retry: false } } });
  client.setQueryData(queryKeys.orders.detail("o1"), order);
  vi.mocked(api.get).mockResolvedValue(order);
});
afterEach(() => { cleanup(); client.clear(); });
function mount() { render(<QueryClientProvider client={client}><CheckoutPanel orderId="o1" /></QueryClientProvider>); }

it("submits mixed payments once, locks inputs and renders the authoritative ticket", async () => {
  let resolve!: (ticket: TicketResponse) => void;
  vi.mocked(api.post).mockReturnValue(new Promise<TicketResponse>((done) => { resolve = done; }));
  mount();
  fireEvent.click(screen.getByRole("button", { name: "Dividir en partes iguales" }));
  fireEvent.change(screen.getByLabelText("Monto recibido 1"), { target: { value: "300" } });
  fireEvent.change(screen.getByLabelText("Monto recibido 2"), { target: { value: "200" } });
  fireEvent.change(screen.getByLabelText("Método 2"), { target: { value: "CARD" } });
  const charge = screen.getByRole("button", { name: "Confirmar cobro" });
  fireEvent.click(charge); fireEvent.click(charge);
  await screen.findByRole("button", { name: "Cobrando…" });
  await waitFor(() => expect(api.post).toHaveBeenCalledTimes(1));
  expect(api.post).toHaveBeenCalledWith("/api/v1/orders/o1/checkout", { tipPercent: 0, payments: [{ method: "CASH", amount: 300 }, { method: "CARD", amount: 200 }] });
  expect((screen.getByLabelText("Monto recibido 1") as HTMLInputElement).closest("fieldset")?.disabled).toBe(true);
  const ticket: TicketResponse = { id: "t1", saleId: "s1", orderId: "o1", folio: "TCK-1", items: [], subtotal: 500,
    total: 500, tip: 0, tipPercent: 0, paymentMethod: "MIXED", closedAt: "2026-10-08T12:00:00Z", change: 0,
    payments: [{ method: "CASH", amount: 300, receivedAmount: 300, change: 0 }, { method: "CARD", amount: 200, receivedAmount: 200, change: 0 }] };
  await act(async () => resolve(ticket));
  await screen.findByText("TCK-1");
  expect(screen.getByText("Cambio")).toBeDefined();
  expect(screen.queryByRole("button", { name: "Confirmar cobro" })).toBeNull();
});
it("keeps an insufficient or noncash-overpaid account disabled", () => {
  mount(); fireEvent.click(screen.getByRole("button", { name: "Pago único exacto" }));
  fireEvent.change(screen.getByLabelText("Monto recibido 1"), { target: { value: "400" } });
  expect((screen.getByRole("button", { name: "Confirmar cobro" }) as HTMLButtonElement).disabled).toBe(true);
  fireEvent.change(screen.getByLabelText("Monto recibido 1"), { target: { value: "600" } });
  expect((screen.getByRole("button", { name: "Confirmar cobro" }) as HTMLButtonElement).disabled).toBe(false);
  fireEvent.change(screen.getByLabelText("Método 1"), { target: { value: "CARD" } });
  expect((screen.getByRole("button", { name: "Confirmar cobro" }) as HTMLButtonElement).disabled).toBe(true);
  expect(api.post).not.toHaveBeenCalled();
});
it("preserves editable payments and displays the server error on rejection", async () => {
  vi.mocked(api.post).mockRejectedValue(new ApiError({ detail: "El monto cambió" }, 422));
  mount(); fireEvent.click(screen.getByRole("button", { name: "Pago único exacto" }));
  fireEvent.click(screen.getByRole("button", { name: "Confirmar cobro" }));
  await screen.findByText("El monto cambió");
  expect((screen.getByLabelText("Monto recibido 1") as HTMLInputElement).value).toBe("500");
  expect((screen.getByRole("button", { name: "Confirmar cobro" }) as HTMLButtonElement).disabled).toBe(false);
});
