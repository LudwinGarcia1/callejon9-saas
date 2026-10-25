import type { CreateProductRequest } from '../api/types';

/**
 * Reglas del formulario de registro de productos. Viven fuera de la pantalla
 * para poder probarlas sin React Native.
 */

export type ProductField = 'name' | 'price' | 'description';
export type ProductFieldErrors = Partial<Record<ProductField, string>>;

export interface ProductFormValues {
  name: string;
  price: string;
  description: string;
}

export const NAME_MAX = 160;
export const DESCRIPTION_MAX = 500;
/** numeric(10,2) en PostgreSQL: hasta 8 enteros y 2 decimales. */
const PRICE_PATTERN = /^\d{1,8}([.,]\d{1,2})?$/;

/** Quita los espacios de los extremos antes de validar y de enviar. */
export function trimValues(values: ProductFormValues): ProductFormValues {
  return { name: values.name.trim(), price: values.price.trim(), description: values.description.trim() };
}

/** Mismas reglas que CreateProductRequest en el backend, para avisar antes de enviar. */
export function validateProduct({ name, price, description }: ProductFormValues): ProductFieldErrors {
  const errors: ProductFieldErrors = {};
  if (!name) errors.name = 'El nombre es obligatorio.';
  else if (name.length > NAME_MAX) errors.name = `Máximo ${NAME_MAX} caracteres.`;
  if (!price) errors.price = 'El precio es obligatorio.';
  else if (!PRICE_PATTERN.test(price)) errors.price = 'Escribe un precio válido, p. ej. 45 o 45.50.';
  if (description.length > DESCRIPTION_MAX) errors.description = `Máximo ${DESCRIPTION_MAX} caracteres.`;
  return errors;
}

/** Cuerpo de POST /api/v1/products: el precio admite coma decimal y los opcionales vacíos no viajan. */
export function toCreateProductRequest(values: ProductFormValues, categoryId: string | null): CreateProductRequest {
  return {
    name: values.name,
    price: Number(values.price.replace(',', '.')),
    description: values.description || undefined,
    categoryId: categoryId ?? undefined,
  };
}
