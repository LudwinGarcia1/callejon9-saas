// @vitest-environment jsdom

import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { afterEach, describe, expect, it, vi } from "vitest";

import { SignupView } from "@/app/signup/signup-view";

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn() }),
}));

afterEach(() => {
  cleanup();
});

function renderSignup() {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  });

  render(
    <QueryClientProvider client={queryClient}>
      <SignupView />
    </QueryClientProvider>,
  );

  return queryClient;
}

describe("SignupView · validación nativa de campos", () => {
  it("marca los datos requeridos y limita el slug a minúsculas, números y guiones", () => {
    const queryClient = renderSignup();
    const restaurantName = screen.getByLabelText("Nombre del restaurante") as HTMLInputElement;
    const slug = screen.getByLabelText("Identificador") as HTMLInputElement;
    const fullName = screen.getByLabelText("Tu nombre completo") as HTMLInputElement;
    const email = screen.getByLabelText("Correo del administrador") as HTMLInputElement;
    const password = screen.getByLabelText("Contraseña") as HTMLInputElement;

    expect(restaurantName.required).toBe(true);
    expect(restaurantName.maxLength).toBe(160);
    expect(slug.required).toBe(true);
    expect(slug.pattern).toBe("[a-z0-9-]{3,80}");
    expect(fullName.required).toBe(true);
    expect(email.required).toBe(true);
    expect(email.type).toBe("email");
    expect(password.required).toBe(true);
    expect(password.maxLength).toBe(72);

    fireEvent.change(slug, { target: { value: "ab" } });
    expect(slug.validity.patternMismatch).toBe(true);

    fireEvent.change(slug, { target: { value: "Mi Restaurante" } });
    expect(slug.validity.patternMismatch).toBe(true);

    fireEvent.change(slug, { target: { value: "mi-restaurante-2026" } });
    expect(slug.checkValidity()).toBe(true);

    fireEvent.change(slug, { target: { value: "a".repeat(81) } });
    expect(slug.validity.patternMismatch).toBe(true);

    fireEvent.change(email, { target: { value: "correo-invalido" } });
    expect(email.checkValidity()).toBe(false);
    fireEvent.change(email, { target: { value: "admin@example.com" } });
    expect(email.checkValidity()).toBe(true);

    queryClient.clear();
  });
});
