// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { LoginView } from "@/app/login/login-view";
import { api, ApiError } from "@/lib/api";

const nav = vi.hoisted(() => ({ push: vi.fn(), params: new URLSearchParams("slug=pruebas") }));
vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: nav.push, replace: vi.fn() }),
  useSearchParams: () => nav.params,
}));
vi.mock("@/lib/api", async (original) => ({
  ...await original<typeof import("@/lib/api")>(),
  api: { get: vi.fn(), post: vi.fn() },
}));

let client: QueryClient;
beforeEach(() => {
  vi.resetAllMocks();
  nav.params = new URLSearchParams("slug=pruebas");
  client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
});
afterEach(() => {
  cleanup();
  client.clear();
});

function mount() {
  render(<QueryClientProvider client={client}><LoginView /></QueryClientProvider>);
  return {
    slug: screen.getByLabelText("Restaurante") as HTMLInputElement,
    email: screen.getByLabelText("Correo") as HTMLInputElement,
    password: screen.getByLabelText("Contraseña") as HTMLInputElement,
    submit: () => fireEvent.click(screen.getByRole("button", { name: "Ingresar" })),
  };
}

function fill(form: ReturnType<typeof mount>, password = "Clave-Erronea-2026") {
  fireEvent.change(form.email, { target: { value: "mesero@pruebas.com" } });
  fireEvent.change(form.password, { target: { value: password } });
}

describe("formulario de login", () => {
  it("tras un 401 borra la contraseña, conserva restaurante y correo y muestra el mensaje único", async () => {
    vi.mocked(api.post).mockRejectedValue(new ApiError({}, 401));
    const form = mount();
    fill(form);

    form.submit();

    expect(await screen.findByText("Restaurante, correo o contraseña incorrectos.")).toBeTruthy();
    expect(form.password.value).toBe("");
    expect(form.slug.value).toBe("pruebas");
    expect(form.email.value).toBe("mesero@pruebas.com");
    expect(api.post).toHaveBeenCalledWith("/api/v1/auth/login",
      { slug: "pruebas", email: "mesero@pruebas.com", password: "Clave-Erronea-2026" });
  });

  it("tras un 429 también borra la contraseña y muestra el aviso de bloqueo del servidor", async () => {
    vi.mocked(api.post).mockRejectedValue(new ApiError(
      { title: "Demasiados intentos", detail: "Demasiados intentos fallidos. Intenta de nuevo en 15 minutos." }, 429));
    const form = mount();
    fill(form);

    form.submit();

    expect(await screen.findByText("Demasiados intentos fallidos. Intenta de nuevo en 15 minutos.")).toBeTruthy();
    expect(screen.getByText("Demasiados intentos")).toBeTruthy();
    expect(form.password.value).toBe("");
  });

  it("si la validación local falla, no envía nada y conserva la contraseña", async () => {
    nav.params = new URLSearchParams("");
    const form = mount();
    fill(form, "una-contraseña-valida-2026");

    form.submit();

    expect(await screen.findByText("Ingresa el identificador del restaurante.")).toBeTruthy();
    expect(api.post).not.toHaveBeenCalled();
    expect(form.password.value).toBe("una-contraseña-valida-2026");
  });

  it("con credenciales válidas entra a la vista de su rol", async () => {
    vi.mocked(api.post).mockResolvedValue({ userId: "u1", fullName: "Mesero de piso", role: "WAITER", twoFactorRequired: false });
    const form = mount();
    fill(form, "Clave-Correcta-2026");

    form.submit();

    await waitFor(() => expect(nav.push).toHaveBeenCalledWith("/waiter"));
  });
});
