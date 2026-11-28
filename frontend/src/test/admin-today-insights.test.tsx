// @vitest-environment jsdom
import { cleanup, render, screen, within } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { AdminView } from "@/app/(authenticated)/admin/admin-view";
import { api } from "@/lib/api";
import { todayIsoDate } from "@/lib/format";
import { queryKeys } from "@/lib/query-keys";
import type { AnalyticsResponse, SessionResponse } from "@/lib/types";

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn(), replace: vi.fn(), refresh: vi.fn() }),
  usePathname: () => "/admin",
}));
vi.mock("sonner", () => ({ toast: { success: vi.fn(), error: vi.fn() } }));
vi.mock("@/lib/api", async (original) => ({
  ...await original<typeof import("@/lib/api")>(),
  api: { get: vi.fn(async () => []), post: vi.fn(), patch: vi.fn(), put: vi.fn() },
}));

const session = { userId: "u1", fullName: "Admin", role: "ADMIN", tenantId: "t1", slug: "pruebas",
  restaurantName: "Pruebas", timezone: "America/Mexico_City" } satisfies SessionResponse;
const today = todayIsoDate();
let client: QueryClient;

function seed(analytics: AnalyticsResponse, sales: { count: number; total: number }) {
  client.setQueryData(queryKeys.session.me(), session);
  client.setQueryData(queryKeys.tables.all(true), [{ id: "m1" }, { id: "m2" }, { id: "m3" }]);
  client.setQueryData(queryKeys.categories.all(), [{ id: "c1" }, { id: "c2" }]);
  client.setQueryData(queryKeys.products.all(true), Array.from({ length: 7 }, (_, i) => ({ id: `p${i}` })));
  client.setQueryData(queryKeys.users.all(), [{ id: "u1" }, { id: "u2" }, { id: "u3" }, { id: "u4" }]);
  client.setQueryData(queryKeys.sales.history(today, today, 0, 1), { content: [], summary: sales });
  client.setQueryData(queryKeys.analytics.summary(today, today), analytics);
}

function mount() {
  render(<QueryClientProvider client={client}><AdminView /></QueryClientProvider>);
}

beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(api.get).mockImplementation(async () => []);
  client = new QueryClient({ defaultOptions: { queries: { retry: false, staleTime: Infinity } } });
});
afterEach(() => { cleanup(); client.clear(); });

it("shows each catalog count next to its tab", () => {
  seed({ pareto: [], salesByDay: [], paymentMix: [], topProducts: [] }, { count: 0, total: 0 });
  mount();

  expect(screen.getByRole("tab", { name: /Mesas/ }).textContent).toContain("3");
  expect(screen.getByRole("tab", { name: /Categorías/ }).textContent).toContain("2");
  expect(screen.getByRole("tab", { name: /Productos/ }).textContent).toContain("7");
  expect(screen.getByRole("tab", { name: /Usuarios/ }).textContent).toContain("4");
});

it("replaces the count cards with today's best seller, top revenue, sales and average ticket", () => {
  seed({
    pareto: [
      { productName: "Torta", revenue: 80, revenueShare: 40, cumulativeShare: 40 },
      { productName: "Taco", revenue: 75, revenueShare: 37.5, cumulativeShare: 77.5 },
      { productName: "Agua", revenue: 45, revenueShare: 22.5, cumulativeShare: 100 },
    ],
    salesByDay: [],
    paymentMix: [],
    topProducts: [
      { productName: "Agua", quantity: 3, revenue: 45 },
      { productName: "Taco", quantity: 3, revenue: 75 },
    ],
  }, { count: 4, total: 200 });
  mount();

  const insights = within(screen.getByRole("region", { name: "Indicadores de hoy" }));
  expect(insights.getByText("Más vendido hoy")).toBeTruthy();
  expect(insights.getByText("Agua", { selector: "[data-slot='card-title']" })).toBeTruthy();
  expect(insights.getByText(/3 unidades/)).toBeTruthy();

  const topRevenue = insights.getAllByRole("listitem").map((row) => row.textContent);
  expect(topRevenue).toHaveLength(3);
  expect(topRevenue[0]).toContain("Torta");
  expect(topRevenue[0]).toContain("40%");

  expect(insights.getByText("Ventas de hoy")).toBeTruthy();
  expect(insights.getByText("Ticket promedio hoy")).toBeTruthy();
  expect(insights.getByText("Sobre 4 ventas.")).toBeTruthy();
});

it("says there are no sales yet instead of showing empty cards", () => {
  seed({ pareto: [], salesByDay: [], paymentMix: [], topProducts: [] }, { count: 0, total: 0 });
  mount();

  const insights = within(screen.getByRole("region", { name: "Indicadores de hoy" }));
  expect(insights.getAllByText("Sin ventas cobradas hoy todavía.").length).toBeGreaterThanOrEqual(2);
});
