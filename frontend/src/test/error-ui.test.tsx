// @vitest-environment jsdom
import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, expect, it } from "vitest";
import { QueryState } from "@/components/shared/query-state";
import { FieldError } from "@/components/shared/field-error";
import { ApiError } from "@/lib/api";

afterEach(cleanup);
it("renders RFC7807 detail and only the matching field error as text", () => {
  const error = new ApiError({ detail: "Revisa la comanda", errors: { quantity: "Cantidad inválida <script>" } }, 422);
  render(<><QueryState isLoading={false} error={error}>Datos</QueryState>
    <FieldError error={error} field="quantity" /><FieldError error={error} field="email" /></>);
  expect(screen.getByText("Revisa la comanda")).toBeDefined();
  expect(screen.getByText("Cantidad inválida <script>")).toBeDefined();
  expect(document.querySelector("script")).toBeNull();
  expect(screen.queryByText("Datos")).toBeNull();
});
it("prefers local validation and suppresses missing field errors", () => {
  const error = new ApiError({ errors: { quantity: "Servidor" } }, 400);
  const { container } = render(<><FieldError error={error} field="quantity" message="Local" />
    <FieldError field="email" /></>);
  expect(screen.getByText("Local")).toBeDefined();
  expect(screen.queryByText("Servidor")).toBeNull();
  expect(container.querySelectorAll("p")).toHaveLength(1);
});
it("distinguishes permission, unknown error, loading, empty and successful states", () => {
  const { rerender } = render(<QueryState isLoading={false} error={new ApiError({ detail: "Interno" }, 403)}>Datos</QueryState>);
  expect(screen.getByText("No tienes permiso para ver esta información.")).toBeDefined();
  rerender(<QueryState isLoading={false} error="unknown">Datos</QueryState>);
  expect(screen.getByText("Ocurrió un error al cargar la información.")).toBeDefined();
  rerender(<QueryState isLoading skeleton={<p>Cargando</p>}>Datos</QueryState>);
  expect(screen.getByText("Cargando")).toBeDefined();
  rerender(<QueryState isLoading={false} isEmpty>Datos</QueryState>);
  expect(screen.getByText("No hay datos para mostrar.")).toBeDefined();
  rerender(<QueryState isLoading={false}>Datos</QueryState>);
  expect(screen.getByText("Datos")).toBeDefined();
});
