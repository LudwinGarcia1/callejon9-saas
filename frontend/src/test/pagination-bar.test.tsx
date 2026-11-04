// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { PaginationBar } from "@/components/shared/pagination-bar";

afterEach(cleanup);

function mount(page: number, totalPages: number, isFetching = false) {
  const onPageChange = vi.fn();
  render(<PaginationBar page={page} totalPages={totalPages} totalElements={60} itemLabel="ventas"
    onPageChange={onPageChange} isFetching={isFetching} />);
  return onPageChange;
}
const button = (name: string) => screen.getByRole("button", { name }) as HTMLButtonElement;

describe("PaginationBar (CAL-16)", () => {
  it("es un nav con nombre propio y anuncia la posición en base 1", () => {
    mount(1, 3);
    expect(screen.getByRole("navigation", { name: "Paginación de ventas" })).toBeTruthy();
    expect(screen.getByText("Página 2 de 3 · 60 ventas")).toBeTruthy();
  });

  it("navega a la página anterior y a la siguiente", () => {
    const onPageChange = mount(1, 3);
    fireEvent.click(button("Anterior"));
    fireEvent.click(button("Siguiente"));
    expect(onPageChange.mock.calls).toEqual([[0], [2]]);
  });

  it("bloquea Anterior en la primera página y Siguiente en la última", () => {
    mount(0, 3);
    expect(button("Anterior").disabled).toBe(true);
    expect(button("Siguiente").disabled).toBe(false);
    cleanup();
    mount(2, 3);
    expect(button("Siguiente").disabled).toBe(true);
  });

  it("bloquea ambos botones mientras llega la página pedida", () => {
    mount(1, 3, true);
    expect(button("Anterior").disabled).toBe(true);
    expect(button("Siguiente").disabled).toBe(true);
  });
});
