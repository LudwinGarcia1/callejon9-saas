// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { HistoryView } from "@/app/(authenticated)/history/history-view";
import { api } from "@/lib/api";
import type { SaleHistoryRow, SalesHistoryResponse } from "@/lib/types";

vi.mock("sonner", () => ({ toast: { success: vi.fn(), error: vi.fn() } }));
vi.mock("@/lib/api", async (original) => ({ ...await original<typeof import("@/lib/api")>(), api: { get: vi.fn() } }));
// El historial calcula "hoy" en la zona del restaurante de la sesion (CAL-19).
vi.mock("@/hooks/use-session", () => ({
  useSession: () => ({ user: { userId: "u1", fullName: "Caja", role: "CASHIER", timezone: "America/Mexico_City" },
    isLoading: false, logout: vi.fn() }),
}));

const TOTAL_SALES = 30;
const PAGE_SIZE = 25;

function sale(index: number): SaleHistoryRow {
  return { id: `s${index}`, orderFolio: `ORD-${index}`, tableNumber: 1, cashierName: "Caja", ticketId: `t${index}`,
    paymentMethod: "CASH", subtotal: 10, tip: 0, total: 10, createdAt: "2026-10-20T18:00:00Z" };
}

/** Simula el backend: pagina el arreglo y responde el resumen del rango completo. */
function salesPage(params: { page?: number; size?: number }): SalesHistoryResponse {
  const page = params.page ?? 0;
  const size = params.size ?? 20;
  const all = Array.from({ length: TOTAL_SALES }, (_, index) => sale(index));
  return {
    sales: { content: all.slice(page * size, (page + 1) * size), page, size, totalElements: all.length,
      totalPages: Math.ceil(all.length / size) },
    summary: { count: TOTAL_SALES, total: TOTAL_SALES * 10 },
    paymentMix: [],
  };
}

let client: QueryClient;
beforeEach(() => {
  vi.resetAllMocks();
  client = new QueryClient({ defaultOptions: { queries: { retry: false, staleTime: Infinity } } });
  vi.mocked(api.get).mockImplementation(async (path: string, params?: Record<string, unknown>) =>
    path === "/api/v1/sales" ? salesPage(params as { page?: number; size?: number }) : null);
});
afterEach(() => { cleanup(); client.clear(); });

function mount() {
  render(<QueryClientProvider client={client}><HistoryView /></QueryClientProvider>);
}
const salesCalls = () => vi.mocked(api.get).mock.calls.filter(([path]) => path === "/api/v1/sales");

describe("historial de ventas paginado (CAL-16)", () => {
  it("pide la primera página con su tamaño y muestra la posición", async () => {
    mount();

    expect(await screen.findByText("ORD-0")).toBeTruthy();
    expect(screen.getByText("Página 1 de 2 · 30 ventas")).toBeTruthy();
    expect(salesCalls()[0][1]).toMatchObject({ page: 0, size: PAGE_SIZE });
    expect(screen.queryByText("ORD-25")).toBeNull();
  });

  it("Siguiente pide la página 1 y el resumen sigue siendo del rango completo", async () => {
    mount();
    await screen.findByText("ORD-0");

    fireEvent.click(screen.getByRole("button", { name: "Siguiente" }));

    expect(await screen.findByText("ORD-25")).toBeTruthy();
    expect(screen.getByText("Página 2 de 2 · 30 ventas")).toBeTruthy();
    expect(salesCalls().at(-1)?.[1]).toMatchObject({ page: 1, size: PAGE_SIZE });
    expect(screen.getByText("30")).toBeTruthy();
  });

  it("cambiar el rango regresa a la primera página", async () => {
    mount();
    await screen.findByText("ORD-0");
    fireEvent.click(screen.getByRole("button", { name: "Siguiente" }));
    await screen.findByText("ORD-25");

    const from = screen.getByLabelText("Desde");
    fireEvent.change(from, { target: { value: "2026-10-01" } });
    // Hay dos "Buscar" (rango y folio): se envia el formulario del rango.
    fireEvent.submit(from.closest("form") as HTMLFormElement);

    await waitFor(() => expect(salesCalls().at(-1)?.[1]).toMatchObject({ from: "2026-10-01", page: 0 }));
    expect(await screen.findByText("ORD-0")).toBeTruthy();
  });

  it("la venta seleccionada conserva su ticket al cambiar de página", async () => {
    vi.mocked(api.get).mockImplementation(async (path: string, params?: Record<string, unknown>) => {
      if (path === "/api/v1/sales") return salesPage(params as { page?: number; size?: number });
      // El detalle del ticket no importa aqui: basta con que el panel siga pidiendolo.
      if (path === "/api/v1/tickets/t3") return new Promise(() => {});
      return null;
    });
    mount();
    fireEvent.click(await screen.findByText("ORD-3"));
    await waitFor(() => expect(api.get).toHaveBeenCalledWith("/api/v1/tickets/t3"));

    fireEvent.click(screen.getByRole("button", { name: "Siguiente" }));
    await screen.findByText("ORD-25");

    expect(screen.queryByText("Selecciona una venta de la lista para ver su ticket.")).toBeNull();
  });
});
