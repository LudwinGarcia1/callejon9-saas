// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { render, screen, fireEvent, cleanup, waitFor } from "@testing-library/react";
import { PrintTablesQrDialog } from "../app/(authenticated)/admin/print-tables-qr-dialog";
import type { TableResponse } from "@/lib/types";

afterEach(cleanup);

vi.mock("@/hooks/use-session", () => ({
  useSession: () => ({
    user: {
      userId: "u1",
      fullName: "Admin Demo",
      role: "ADMIN",
      tenantId: "tenant-demo",
      slug: "tacos-demo",
      restaurantName: "Tacos el Rey",
    },
    isLoading: false,
    logout: vi.fn(),
  }),
}));

vi.mock("qrcode", () => ({
  default: {
    toDataURL: vi.fn().mockResolvedValue("data:image/png;base64,mockqrdata"),
  },
}));

describe("PrintTablesQrDialog", () => {
  const mockTables: TableResponse[] = [
    { id: "1", number: 1, capacity: 4, active: true, restaurantId: "r1" },
    { id: "2", number: 2, capacity: 2, active: true, restaurantId: "r1" },
    { id: "3", number: 3, capacity: 6, active: false, restaurantId: "r1" },
  ];

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("renderiza el botón para abrir el diálogo de impresión de QR", () => {
    render(<PrintTablesQrDialog tables={mockTables} />);
    const triggerBtn = screen.getByRole("button", { name: /imprimir qr de mesas/i });
    expect(triggerBtn).toBeDefined();
  });

  it("abre el diálogo y genera los QR únicamente para las mesas activas", async () => {
    render(<PrintTablesQrDialog tables={mockTables} />);
    const triggerBtn = screen.getByRole("button", { name: /imprimir qr de mesas/i });
    fireEvent.click(triggerBtn);

    // Debe mostrar el título del modal
    expect(screen.getByText(/Tarjetas QR para Mesas/i)).toBeDefined();

    // Debe mostrar únicamente las mesas activas (Mesa 1 y Mesa 2)
    await waitFor(() => {
      expect(screen.getByText("MESA 1")).toBeDefined();
      expect(screen.getByText("MESA 2")).toBeDefined();
      expect(screen.queryByText("MESA 3")).toBeNull();
    });

    // Debe haber botón para mandar a imprimir
    expect(screen.getByRole("button", { name: /mandar a imprimir/i })).toBeDefined();
  });
});
