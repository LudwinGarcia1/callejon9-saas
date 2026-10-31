// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, expect, it, vi } from "vitest";
import { ProductPicker, type CartLine } from "@/app/(authenticated)/waiter/order/[id]/product-picker";

const sopa = { id: "p-sopa", name: "Sopa de tortilla", price: 85, active: true, categoryId: null, description: null };

afterEach(cleanup);

function mount(cart: CartLine[], onNotesChange = vi.fn()) {
  render(
    <ProductPicker
      categories={[]}
      products={[sopa]}
      isLoading={false}
      cart={cart}
      onAddProduct={vi.fn()}
      onIncrement={vi.fn()}
      onDecrement={vi.fn()}
      onRemove={vi.fn()}
      onNotesChange={onNotesChange}
      onCommit={vi.fn()}
      isCommitting={false}
      disabled={false}
      alreadySentToKitchen={false}
    />,
  );
  return onNotesChange;
}

it("cada línea del carrito tiene su campo de nota, con el tope de 500 del backend (CAL-151)", () => {
  const onNotesChange = mount([
    { key: "a", product: sopa, quantity: 1, notes: "sin crema" },
    { key: "b", product: sopa, quantity: 1, notes: "" },
  ]);

  const fields = screen.getAllByRole("textbox", { name: "Nota para Sopa de tortilla" }) as HTMLInputElement[];
  expect(fields).toHaveLength(2);
  expect(fields[0].value).toBe("sin crema");
  expect(fields[1].maxLength).toBe(500);

  fireEvent.change(fields[1], { target: { value: "extra picante" } });
  expect(onNotesChange).toHaveBeenCalledWith("b", "extra picante");
});
