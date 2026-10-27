// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { afterEach, beforeEach, expect, it, vi } from "vitest";

import { InventoryView } from "@/app/(authenticated)/inventory/inventory-view";
import { api } from "@/lib/api";
import type { InventoryItemResponse, RegisterMovementRequest, RegisteredMovementResponse } from "@/lib/types";

const session = vi.hoisted(() => ({ role: "KITCHEN" }));
const effects = vi.hoisted(() => ({ success: vi.fn(), error: vi.fn() }));
vi.mock("@/hooks/use-session", () => ({
  useSession: () => ({ user: { role: session.role, userId: "test-user" } }),
}));
vi.mock("sonner", () => ({ toast: effects }));
vi.mock("@/lib/api", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/lib/api")>();
  return { ...actual, api: { ...actual.api, get: vi.fn(), post: vi.fn() } };
});

let client: QueryClient;
let currentItem: InventoryItemResponse;

beforeEach(() => {
  vi.resetAllMocks();
  session.role = "KITCHEN";
  Object.defineProperty(HTMLElement.prototype, "scrollIntoView", {
    configurable: true,
    value: vi.fn(),
  });
  currentItem = {
    id: "item-rice",
    name: "Arroz",
    unit: "kg",
    stock: 10,
    minStock: 5,
    unitCost: 32,
    active: true,
    level: "OK",
  };
  client = new QueryClient({
    defaultOptions: { queries: { retry: false, staleTime: Infinity }, mutations: { retry: false } },
  });

  vi.mocked(api.get).mockImplementation(async (path) =>
    path === "/api/v1/inventory/items" ? [currentItem] : [],
  );
  vi.mocked(api.post).mockImplementation(async (_path, request) => {
    const movement = request as RegisterMovementRequest;
    if (movement.movementType === "OUT") {
      currentItem = {
        ...currentItem,
        stock: currentItem.stock - (movement.quantity ?? 0),
        level: "LOW",
      };
    } else if (movement.movementType === "ADJUSTMENT") {
      currentItem = { ...currentItem, stock: movement.countedStock ?? currentItem.stock, level: "LOW" };
    }

    return {
      id: `movement-${movement.movementType.toLowerCase()}`,
      inventoryItemId: currentItem.id,
      movementType: movement.movementType,
      quantity: movement.quantity ?? 2,
      reason: null,
      createdAt: "2026-10-27T12:00:00Z",
    } satisfies RegisteredMovementResponse;
  });
});

afterEach(() => {
  cleanup();
  client.clear();
});

function inventoryTree() {
  return (
    <QueryClientProvider client={client}>
      <InventoryView />
    </QueryClientProvider>
  );
}

async function chooseMovementType(label: string) {
  fireEvent.click(screen.getByRole("combobox"));
  fireEvent.click(await screen.findByRole("option", { name: label }));
}

it("la cocina registra una salida y el administrador ve el stock actualizado, ajusta el conteo y detecta el mínimo", async () => {
  const view = render(inventoryTree());

  expect(await screen.findByText("10 kg")).toBeTruthy();
  expect(screen.getByText("Suficiente")).toBeTruthy();

  fireEvent.click(screen.getByRole("button", { name: "Movimiento" }));
  await screen.findByRole("dialog");
  await chooseMovementType("Salida");
  fireEvent.change(screen.getByLabelText("Cantidad (kg)"), { target: { value: "8" } });
  fireEvent.click(screen.getByRole("button", { name: "Registrar" }));

  await waitFor(() => expect(api.post).toHaveBeenCalledWith("/api/v1/inventory/movements", {
    inventoryItemId: "item-rice",
    movementType: "OUT",
    quantity: 8,
    countedStock: undefined,
    reason: undefined,
  }));
  expect(await screen.findByText("2 kg")).toBeTruthy();
  expect(screen.getByText("Bajo m\u00ednimo")).toBeTruthy();

  session.role = "ADMIN";
  view.rerender(inventoryTree());
  expect(await screen.findByText("2 kg")).toBeTruthy();

  fireEvent.click(screen.getByRole("button", { name: "Movimiento" }));
  await screen.findByRole("dialog");
  await chooseMovementType("Ajuste");
  fireEvent.change(screen.getByLabelText("Conteo fisico"), { target: { value: "4" } });
  fireEvent.click(screen.getByRole("button", { name: "Registrar" }));

  await waitFor(() => expect(api.post).toHaveBeenLastCalledWith("/api/v1/inventory/movements", {
    inventoryItemId: "item-rice",
    movementType: "ADJUSTMENT",
    quantity: undefined,
    countedStock: 4,
    reason: undefined,
  }));
  expect(await screen.findByText("4 kg")).toBeTruthy();
  expect(screen.getByText("Bajo m\u00ednimo")).toBeTruthy();
});
