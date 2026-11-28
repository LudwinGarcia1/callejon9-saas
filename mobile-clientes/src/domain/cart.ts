import type { OrderItemRequest, Product } from '../api/types';

/** Topes de AddOrderItemRequest en el backend. */
export const MAX_QUANTITY = 99;
export const MAX_NOTES_LENGTH = 500;
export const MAX_LINES = 50;

export interface CartLine {
  productId: string;
  name: string;
  /** Precio al agregarlo. El backend cobra el precio vigente al recibir el pedido. */
  unitPrice: number;
  quantity: number;
  notes: string;
}

export type Cart = CartLine[];

export function addProduct(cart: Cart, product: Product): Cart {
  const existing = cart.find((line) => line.productId === product.id);
  if (existing) {
    return setQuantity(cart, product.id, existing.quantity + 1);
  }
  if (cart.length >= MAX_LINES) {
    return cart;
  }
  return [...cart, { productId: product.id, name: product.name, unitPrice: Number(product.price), quantity: 1, notes: '' }];
}

/** Una cantidad de 0 o menos quita la línea; arriba del tope se recorta. */
export function setQuantity(cart: Cart, productId: string, quantity: number): Cart {
  if (quantity <= 0) {
    return cart.filter((line) => line.productId !== productId);
  }
  const bounded = Math.min(Math.floor(quantity), MAX_QUANTITY);
  return cart.map((line) => (line.productId === productId ? { ...line, quantity: bounded } : line));
}

export function setNotes(cart: Cart, productId: string, notes: string): Cart {
  const bounded = notes.slice(0, MAX_NOTES_LENGTH);
  return cart.map((line) => (line.productId === productId ? { ...line, notes: bounded } : line));
}

export function quantityOf(cart: Cart, productId: string): number {
  return cart.find((line) => line.productId === productId)?.quantity ?? 0;
}

export function cartCount(cart: Cart): number {
  return cart.reduce((sum, line) => sum + line.quantity, 0);
}

export function cartTotal(cart: Cart): number {
  return cart.reduce((sum, line) => sum + line.unitPrice * line.quantity, 0);
}

export function toOrderItems(cart: Cart): OrderItemRequest[] {
  return cart.map((line) => {
    const notes = line.notes.trim();
    return { productId: line.productId, quantity: line.quantity, ...(notes ? { notes } : {}) };
  });
}

/**
 * Al recibir un menú nuevo, quita del carrito lo que ya no se vende y actualiza
 * nombre y precio de lo demás.
 */
export function reconcileWithCatalog(cart: Cart, products: Product[]): Cart {
  const byId = new Map(products.filter((p) => p.active).map((p) => [p.id, p]));
  return cart
    .filter((line) => byId.has(line.productId))
    .map((line) => {
      const product = byId.get(line.productId)!;
      return { ...line, name: product.name, unitPrice: Number(product.price) };
    });
}
