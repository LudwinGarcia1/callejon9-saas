/**
 * Validación del formulario de producto antes de enviarlo. Replica las reglas
 * de CreateProductRequest y UpdateProductRequest del backend para avisar sin
 * gastar una petición; el backend las vuelve a aplicar con @Valid.
 */

export type Field = 'name' | 'price' | 'description';
export type FieldErrors = Partial<Record<Field, string>>;

export const NAME_MAX = 160;
export const DESCRIPTION_MAX = 500;
/** numeric(10,2) en PostgreSQL: hasta 8 enteros y 2 decimales. */
const PRICE_PATTERN = /^\d{1,8}([.,]\d{1,2})?$/;

/** Recibe los valores ya recortados (trim). Un objeto vacío significa que se puede enviar. */
export function validateProduct(name: string, price: string, description: string): FieldErrors {
  const errors: FieldErrors = {};
  if (!name) errors.name = 'El nombre es obligatorio.';
  else if (name.length > NAME_MAX) errors.name = `Máximo ${NAME_MAX} caracteres.`;
  if (!price) errors.price = 'El precio es obligatorio.';
  else if (!PRICE_PATTERN.test(price)) errors.price = 'Escribe un precio válido, p. ej. 45 o 45.50.';
  if (description.length > DESCRIPTION_MAX) errors.description = `Máximo ${DESCRIPTION_MAX} caracteres.`;
  return errors;
}
