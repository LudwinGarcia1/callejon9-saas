// @vitest-environment jsdom
import { act, cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { SignupView } from "@/app/signup/signup-view";
import { api, ApiError } from "@/lib/api";

const effects = vi.hoisted(() => ({ push: vi.fn() }));
vi.mock("next/navigation", () => ({ useRouter: () => ({ push: effects.push }) }));
vi.mock("@/lib/api", async (original) => ({
  ...(await original<typeof import("@/lib/api")>()),
  api: { get: vi.fn(), post: vi.fn() },
}));

const valid = {
  restaurantName: "La Esquina",
  slug: "la-esquina",
  adminFullName: "Ana Torres",
  adminEmail: "ana@esquina.mx",
  password: "Mantel-Azul-47",
};

let client: QueryClient;
beforeEach(() => {
  vi.resetAllMocks();
  client = new QueryClient({ defaultOptions: { mutations: { retry: false } } });
});
afterEach(() => {
  cleanup();
  client.clear();
});

function mount() {
  render(
    <QueryClientProvider client={client}>
      <SignupView />
    </QueryClientProvider>,
  );
}

function input(name: keyof typeof valid): HTMLInputElement {
  return document.querySelector(`input[name="${name}"]`) as HTMLInputElement;
}

/** Llena el formulario y lo envía; `change` en cada campo dispara el `onChange` del form. */
function submit(values: Partial<typeof valid> = {}) {
  const data = { ...valid, ...values };
  for (const [name, value] of Object.entries(data)) {
    fireEvent.change(input(name as keyof typeof valid), { target: { value } });
  }
  fireEvent.submit(document.querySelector("form") as HTMLFormElement);
}

/** El navegador evalúa `pattern` como `^(?:pattern)$` sobre el valor completo. */
function slugAccepted(value: string): boolean {
  return new RegExp(`^(?:${input("slug").pattern})$`, "v").test(value);
}

describe("formulario de alta de restaurante: reglas del identificador", () => {
  it.each([
    ["abc", true, "3 caracteres, el mínimo"],
    ["ab", false, "2 caracteres"],
    ["a".repeat(80), true, "80 caracteres, el máximo"],
    ["a".repeat(81), false, "81 caracteres"],
    ["la-esquina-2", true, "minúsculas, números y guiones"],
    ["La-Esquina", false, "mayúsculas"],
    ["la_esquina", false, "guion bajo"],
    ["la esquina", false, "espacio"],
    ["cafe-niño", false, "letra ñ"],
  ])("«%s» aceptado = %s (%s), igual que @Pattern del backend", (value, accepted) => {
    mount();
    expect(slugAccepted(value)).toBe(accepted);
    expect(slugAccepted(value)).toBe(/^[a-z0-9-]{3,80}$/.test(value));
  });

  it("los campos son obligatorios y sus topes coinciden con SignupRequest", () => {
    mount();
    for (const name of Object.keys(valid) as (keyof typeof valid)[]) {
      expect(input(name).required).toBe(true);
    }
    expect(input("restaurantName").maxLength).toBe(160);
    expect(input("adminFullName").maxLength).toBe(160);
    expect(input("adminEmail").maxLength).toBe(180);
    expect(input("adminEmail").type).toBe("email");
    expect(input("password").maxLength).toBe(72);
  });
});

describe("formulario de alta de restaurante: envío", () => {
  it.each([
    ["corta", "Azul-47", "al menos 10 caracteres"],
    ["sin números", "Mantel-Azul-Rojo", "combinar letras y números"],
    ["con el identificador", "esquina-2026-x", "no debe incluir tu nombre"],
  ])("una contraseña %s se rechaza en el navegador y no llama a la API", async (_, password, message) => {
    mount();
    submit({ password });

    expect(await screen.findByText(new RegExp(message))).toBeTruthy();
    expect(input("password").getAttribute("aria-invalid")).toBe("true");
    expect(api.post).not.toHaveBeenCalled();
  });

  it("un alta válida envía el plan elegido y lleva al login con el identificador", async () => {
    vi.mocked(api.post).mockResolvedValue({ tenantId: "t1", slug: "la-esquina", adminEmail: valid.adminEmail });
    mount();

    expect(screen.getByText("3 usuarios · 5 mesas")).toBeTruthy();
    expect(screen.getByText("15 usuarios · 30 mesas")).toBeTruthy();
    expect(screen.getByText("60 usuarios · 120 mesas")).toBeTruthy();
    fireEvent.click(screen.getByRole("radio", { name: /Gratis/ }));
    submit();

    await waitFor(() => expect(api.post).toHaveBeenCalledTimes(1));
    expect(api.post).toHaveBeenCalledWith("/api/v1/signup", { ...valid, planCode: "FREE" });
    await waitFor(() => expect(effects.push).toHaveBeenCalledWith("/login?slug=la-esquina"));
  });

  it("sin elegir plan se envía PRO, el plan preseleccionado", async () => {
    vi.mocked(api.post).mockResolvedValue({ tenantId: "t1", slug: "la-esquina", adminEmail: valid.adminEmail });
    mount();
    submit();

    await waitFor(() => expect(api.post).toHaveBeenCalledWith("/api/v1/signup", { ...valid, planCode: "PRO" }));
  });

  it("un 400 con errores por campo muestra el mensaje debajo del identificador, sin aviso general", async () => {
    const problem = { detail: "Datos invalidos", errors: { slug: "Solo minusculas, numeros y guiones, entre 3 y 80 caracteres." } };
    vi.mocked(api.post).mockRejectedValue(new ApiError(problem, 400));
    mount();
    await act(async () => submit());

    expect(await screen.findByText(problem.errors.slug)).toBeTruthy();
    expect(screen.queryByText("No se pudo completar el registro")).toBeNull();
  });

  it("un 409 por identificador ocupado muestra el aviso general con el detalle del servidor", async () => {
    vi.mocked(api.post).mockRejectedValue(new ApiError({ detail: "Ya existe un restaurante con el identificador 'la-esquina'." }, 409));
    mount();
    await act(async () => submit());

    expect(await screen.findByText("No se pudo completar el registro")).toBeTruthy();
    expect(screen.getByText("Ya existe un restaurante con el identificador 'la-esquina'.")).toBeTruthy();
    expect(effects.push).not.toHaveBeenCalled();
  });
});

// Cobertura adicional de la rama CAL-121 original: validacion nativa del correo.
describe("SignupView: validacion nativa del correo", () => {
  it("rechaza un correo invalido y acepta uno valido", () => {
    mount();
    fireEvent.change(input("adminEmail"), { target: { value: "correo-invalido" } });
    expect(input("adminEmail").checkValidity()).toBe(false);
    fireEvent.change(input("adminEmail"), { target: { value: "admin@example.com" } });
    expect(input("adminEmail").checkValidity()).toBe(true);
  });
});
