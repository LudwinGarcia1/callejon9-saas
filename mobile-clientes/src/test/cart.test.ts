import { describe, expect, it } from 'vitest';
import type { Product } from '../api/types';
import {
  addProduct, cartCount, cartTotal, MAX_NOTES_LENGTH, MAX_QUANTITY, reconcileWithCatalog, setNotes, setQuantity, toOrderItems,
} from '../domain/cart';

const taco: Product = { id: 'p1', name: 'Taco', description: null, price: 25, categoryId: null, active: true };
const agua: Product = { id: 'p2', name: 'Agua', description: null, price: 18.5, categoryId: null, active: true };

describe('carrito', () => {
  it('suma cantidades del mismo producto y calcula el total', () => {
    const cart = addProduct(addProduct(addProduct([], taco), taco), agua);
    expect(cart).toHaveLength(2);
    expect(cartCount(cart)).toBe(3);
    expect(cartTotal(cart)).toBe(68.5);
  });

  it('quita la línea con cantidad 0 y recorta al tope del backend', () => {
    const cart = addProduct([], taco);
    expect(setQuantity(cart, 'p1', 0)).toEqual([]);
    expect(setQuantity(cart, 'p1', 500)[0].quantity).toBe(MAX_QUANTITY);
  });

  it('recorta las notas y omite las vacías al armar el pedido', () => {
    let cart = addProduct(addProduct([], taco), agua);
    cart = setNotes(cart, 'p1', 'x'.repeat(MAX_NOTES_LENGTH + 20));
    cart = setNotes(cart, 'p2', '   ');
    const items = toOrderItems(cart);
    expect(items[0].notes).toHaveLength(MAX_NOTES_LENGTH);
    expect(items[1]).toEqual({ productId: 'p2', quantity: 1 });
  });

  it('al llegar un menú nuevo quita lo inactivo o retirado y actualiza precios', () => {
    const cart = addProduct(addProduct([], taco), agua);
    const result = reconcileWithCatalog(cart, [{ ...taco, price: 30 }, { ...agua, active: false }]);
    expect(result).toEqual([{ productId: 'p1', name: 'Taco', unitPrice: 30, quantity: 1, notes: '' }]);
  });
});
