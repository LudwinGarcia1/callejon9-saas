// @vitest-environment jsdom
import { render, screen, fireEvent, waitFor, cleanup } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { MercadoPagoQrModal } from "@/components/shared/mercadopago-qr-modal";
import { api } from "@/lib/api";

vi.mock("@/lib/api", () => ({
  api: {
    post: vi.fn(),
    get: vi.fn(),
  },
}));

vi.mock("qrcode", () => ({
  default: {
    toDataURL: vi.fn().mockResolvedValue("data:image/png;base64,mockqr"),
  },
}));

vi.mock("sonner", () => ({
  toast: {
    success: vi.fn(),
    error: vi.fn(),
    info: vi.fn(),
  },
}));

describe("MercadoPagoQrModal", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it("genera la preferencia y muestra el QR y total", async () => {
    vi.mocked(api.post).mockResolvedValueOnce({
      preferenceId: "PREF-12345",
      initPoint: "https://www.mercadopago.com.mx/checkout/12345",
      sandboxInitPoint: "https://sandbox.mercadopago.com.mx/checkout/12345",
      qrCode: "mock",
      externalReference: "tenant:order",
      total: 350.5,
      folio: "ORD-0010",
      isMock: true,
    });

    render(
      <MercadoPagoQrModal
        orderId="11111111-1111-1111-1111-111111111111"
        tipPercent={10}
        isOpen={true}
        onClose={vi.fn()}
        onPaid={vi.fn()}
      />
    );

    await waitFor(() => {
      expect(screen.getByText("Cobro con Mercado Pago")).toBeDefined();
    });

    expect(screen.getByText("$350.50")).toBeDefined();
    expect(screen.getByText(/ORD-0010/)).toBeDefined();
    expect(screen.getByText(/Simular Aprobación/i)).toBeDefined();
  });

  it("permite simular la aprobación en modo mock", async () => {
    vi.mocked(api.post)
      .mockResolvedValueOnce({
        preferenceId: "PREF-MOCK",
        initPoint: "https://mp.com/mock",
        sandboxInitPoint: "https://mp.com/mock",
        qrCode: "mock",
        externalReference: "tenant:order",
        total: 100,
        folio: "ORD-0001",
        isMock: true,
      })
      .mockResolvedValueOnce({
        orderId: "11111111-1111-1111-1111-111111111111",
        folio: "ORD-0001",
        status: "PAID",
        paid: true,
        ticketFolio: "TCK-9999",
        closedAt: new Date().toISOString(),
      });

    const onPaidMock = vi.fn();

    render(
      <MercadoPagoQrModal
        orderId="11111111-1111-1111-1111-111111111111"
        tipPercent={0}
        isOpen={true}
        onClose={vi.fn()}
        onPaid={onPaidMock}
      />
    );

    const simBtn = await screen.findByRole("button", { name: /simular aprobación/i });
    fireEvent.click(simBtn);

    await waitFor(() => {
      expect(screen.getByText("¡Pago Confirmado!")).toBeDefined();
    });
  });

  it("permite verificar el pago manualmente en modo real", async () => {
    vi.mocked(api.post)
      .mockResolvedValueOnce({
        preferenceId: "PREF-REAL",
        initPoint: "https://sandbox.mercadopago.com.mx/checkout/real",
        sandboxInitPoint: "https://sandbox.mercadopago.com.mx/checkout/real",
        qrCode: "mock",
        externalReference: "tenant:order:10:123456",
        total: 250,
        folio: "ORD-0005",
        isMock: false,
      })
      .mockResolvedValueOnce({
        orderId: "11111111-1111-1111-1111-111111111111",
        folio: "ORD-0005",
        status: "PAID",
        paid: true,
        ticketFolio: "TCK-1001",
        closedAt: new Date().toISOString(),
      });

    const onPaidMock = vi.fn();

    render(
      <MercadoPagoQrModal
        orderId="11111111-1111-1111-1111-111111111111"
        tipPercent={10}
        isOpen={true}
        onClose={vi.fn()}
        onPaid={onPaidMock}
      />
    );

    const verifyBtn = await screen.findByRole("button", { name: /ya pagué \/ verificar ahora/i });
    expect(verifyBtn).toBeDefined();
    expect(screen.queryByRole("button", { name: /simular aprobación/i })).toBeNull();

    fireEvent.click(verifyBtn);

    await waitFor(() => {
      expect(screen.getByText("¡Pago Confirmado!")).toBeDefined();
    });
  });
});
