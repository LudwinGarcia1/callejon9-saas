// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen, within } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { afterEach, expect, it, vi } from "vitest";

import { CreateUserDialog } from "@/app/(authenticated)/admin/create-user-dialog";
import { api } from "@/lib/api";

vi.mock("@/lib/api", async (original) => ({
  ...(await original<typeof import("@/lib/api")>()),
  api: { post: vi.fn() },
}));

afterEach(() => {
  cleanup();
  vi.resetAllMocks();
});

it("explica la longitud mínima y anuncia el error junto al campo de contraseña", () => {
  const client = new QueryClient();
  render(
    <QueryClientProvider client={client}>
      <CreateUserDialog />
    </QueryClientProvider>,
  );

  fireEvent.click(screen.getByRole("button", { name: "Nuevo usuario" }));
  const dialog = within(screen.getByRole("dialog", { name: "Nuevo usuario" }));
  fireEvent.change(dialog.getByLabelText("Nombre completo"), { target: { value: "Cuenta de prueba" } });
  fireEvent.change(dialog.getByLabelText("Correo"), { target: { value: "rol@example.test" } });
  const password = dialog.getByLabelText("Contraseña");
  fireEvent.change(password, { target: { value: "corto1" } });
  fireEvent.click(dialog.getByRole("button", { name: "Crear usuario" }));

  const error = dialog.getByText("La contraseña debe tener al menos 10 caracteres.");
  expect(error.parentElement?.getAttribute("aria-live")).toBe("polite");
  expect(password.getAttribute("aria-describedby")?.split(" ")).toContain(error.parentElement?.id);
  expect(password.getAttribute("aria-invalid")).toBe("true");
  expect(api.post).not.toHaveBeenCalled();

  fireEvent.change(password, { target: { value: "ejemplo42largo" } });
  expect(dialog.queryByText("La contraseña debe tener al menos 10 caracteres.")).toBeNull();
  expect(password.getAttribute("aria-invalid")).toBe("false");
});
