// @vitest-environment jsdom
import { render, screen, fireEvent, waitFor, cleanup } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import MercadoPagoMockPage from "@/app/payment/mercadopago/mock/page";
import { api } from "@/lib/api";

vi.mock("@/lib/api", () => ({
  api: {
    post: vi.fn(),
  },
}));

vi.mock("next/navigation", () => ({
  useSearchParams: () => ({
    get: (key: string) => {
      if (key === "pref_id") return "MOCK-PREF-12345";
      if (key === "ref") return "1111:2222:10:9999";
      if (key === "total") return "250.00";
      if (key === "folio") return "ORD-0099";
      return null;
    },
  }),
}));

vi.mock("sonner", () => ({
  toast: {
    success: vi.fn(),
    error: vi.fn(),
  },
}));

describe("MercadoPagoMockPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it("renderiza el checkout sandbox con folio y total", () => {
    render(<MercadoPagoMockPage />);

    expect(screen.getByText("Mercado Pago")).toBeDefined();
    expect(screen.getByText("ORD-0099")).toBeDefined();
    expect(screen.getByText("$250.00")).toBeDefined();
    expect(screen.getByRole("button", { name: /pagar \$250\.00 mxn/i })).toBeDefined();
  });

  it("procesa el pago simulado exitosamente hacia el webhook", async () => {
    vi.mocked(api.post).mockResolvedValueOnce({ status: "received", processed: true });

    render(<MercadoPagoMockPage />);

    const payButton = screen.getByRole("button", { name: /pagar \$250\.00 mxn/i });
    fireEvent.click(payButton);

    await waitFor(() => {
      expect(api.post).toHaveBeenCalledWith(
        "/api/v1/webhooks/mercadopago",
        expect.objectContaining({
          action: "payment.created",
          external_reference: "1111:2222:10:9999",
        })
      );
      expect(screen.getByText("¡Pago Aprobado!")).toBeDefined();
    });
  });

  it("permite cancelar el pago", async () => {
    render(<MercadoPagoMockPage />);

    const cancelButton = screen.getByRole("button", { name: /cancelar y regresar/i });
    fireEvent.click(cancelButton);

    await waitFor(() => {
      expect(screen.getByText("Pago Cancelado")).toBeDefined();
    });
  });
});
