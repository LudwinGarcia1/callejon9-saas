// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { render, screen, fireEvent, cleanup, waitFor } from "@testing-library/react";
import { DinerMenuView } from "../app/m/[slug]/[table]/diner-menu-view";

afterEach(cleanup);

vi.mock("sonner", () => ({
  toast: {
    success: vi.fn(),
    info: vi.fn(),
    error: vi.fn(),
  },
}));

describe("DinerMenuView (Auto-pedido y Menú QR)", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("renderiza correctamente el slug del restaurante y el número de mesa", () => {
    render(<DinerMenuView slug="los-taquitos" tableNumber="5" />);

    expect(screen.getByText("los taquitos")).toBeDefined();
    expect(screen.getByText("Mesa 5")).toBeDefined();
    expect(screen.getByText("RestoHub")).toBeDefined();
  });

  it("permite agregar productos a la comanda y actualiza la barra flotante", () => {
    render(<DinerMenuView slug="los-taquitos" tableNumber="3" />);

    // Buscar botones de "Agregar"
    const addButtons = screen.getAllByRole("button", { name: /agregar/i });
    expect(addButtons.length).toBeGreaterThan(0);

    // Agregar el primer platillo
    fireEvent.click(addButtons[0]);

    // Debe mostrar la barra de comanda con 1 producto
    expect(screen.getByText(/ver comanda de la mesa 3/i)).toBeDefined();
  });

  it("permite llamar al mesero y confirma cuando el servidor recibe la llamada", async () => {
    const { toast } = await import("sonner");
    const { api } = await import("@/lib/api");
    const post = vi.spyOn(api, "post").mockResolvedValue({ success: true, tableNumber: 2 });
    render(<DinerMenuView slug="los-taquitos" tableNumber="2" />);

    fireEvent.click(screen.getByRole("button", { name: /mesero/i }));

    expect(post).toHaveBeenCalledWith("/api/v1/public/los-taquitos/tables/2/call-waiter", {});
    await waitFor(() => expect(toast.info).toHaveBeenCalledWith(
      expect.stringContaining("Mesero notificado"),
      expect.objectContaining({
        description: expect.stringContaining("Mesa 2"),
      })
    ));
    post.mockRestore();
  });

  it("avisa al comensal si la llamada al mesero no llega al servidor", async () => {
    const { toast } = await import("sonner");
    const { api } = await import("@/lib/api");
    const post = vi.spyOn(api, "post").mockRejectedValue(new Error("sin red"));
    render(<DinerMenuView slug="los-taquitos" tableNumber="2" />);

    fireEvent.click(screen.getByRole("button", { name: /mesero/i }));

    await waitFor(() => expect(toast.error).toHaveBeenCalledWith(
      expect.stringContaining("No se pudo avisar al mesero"),
    ));
    expect(toast.info).not.toHaveBeenCalledWith(expect.stringContaining("Mesero notificado"), expect.anything());
    post.mockRestore();
  });

  it("permite pedir la cuenta y notifica a caja", async () => {
    const { toast } = await import("sonner");
    render(<DinerMenuView slug="los-taquitos" tableNumber="8" />);

    const askBillBtn = screen.getByRole("button", { name: /cuenta/i });
    fireEvent.click(askBillBtn);

    expect(toast.info).toHaveBeenCalledWith(
      expect.stringContaining("Solicitud de cuenta enviada"),
      expect.objectContaining({
        description: expect.stringContaining("Mesa 8"),
      })
    );
  });
});
