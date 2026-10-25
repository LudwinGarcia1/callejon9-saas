import { describe, expect, it } from 'vitest';
import {
  DESCRIPTION_MAX,
  NAME_MAX,
  toCreateProductRequest,
  trimValues,
  validateProduct,
} from '../screens/productFormRules';

const valid = { name: 'Taco de bistec', price: '45.50', description: '' };

describe('formulario de producto: campos obligatorios', () => {
  it('un formulario vacío marca nombre y precio, y nada más', () => {
    expect(validateProduct({ name: '', price: '', description: '' })).toEqual({
      name: 'El nombre es obligatorio.',
      price: 'El precio es obligatorio.',
    });
  });

  it('con solo el nombre, falta el precio', () => {
    expect(validateProduct({ ...valid, price: '' })).toEqual({ price: 'El precio es obligatorio.' });
  });

  it('un nombre de puros espacios cuenta como vacío', () => {
    expect(validateProduct(trimValues({ ...valid, name: '   ' }))).toEqual({ name: 'El nombre es obligatorio.' });
  });

  it('un formulario completo no tiene avisos; la descripción y la categoría son opcionales', () => {
    expect(validateProduct(valid)).toEqual({});
  });
});

describe('formulario de producto: precio', () => {
  it.each(['0', '45', '45.5', '45.50', '45,50', '99999999.99'])('acepta %s', (price) => {
    expect(validateProduct({ ...valid, price })).toEqual({});
  });

  it.each(['abc', '-5', '45.555', '123456789', '45.', '.50', '4 5', '$45'])('rechaza %s', (price) => {
    expect(validateProduct({ ...valid, price })).toEqual({ price: 'Escribe un precio válido, p. ej. 45 o 45.50.' });
  });
});

describe('formulario de producto: longitudes', () => {
  it(`el nombre admite ${NAME_MAX} caracteres y rechaza uno más`, () => {
    expect(validateProduct({ ...valid, name: 'a'.repeat(NAME_MAX) })).toEqual({});
    expect(validateProduct({ ...valid, name: 'a'.repeat(NAME_MAX + 1) })).toEqual({ name: `Máximo ${NAME_MAX} caracteres.` });
  });

  it(`la descripción admite ${DESCRIPTION_MAX} caracteres y rechaza uno más`, () => {
    expect(validateProduct({ ...valid, description: 'a'.repeat(DESCRIPTION_MAX) })).toEqual({});
    expect(validateProduct({ ...valid, description: 'a'.repeat(DESCRIPTION_MAX + 1) })).toEqual({
      description: `Máximo ${DESCRIPTION_MAX} caracteres.`,
    });
  });
});

describe('formulario de producto: cuerpo del POST', () => {
  it('convierte la coma decimal y no envía los opcionales vacíos', () => {
    const body = toCreateProductRequest({ name: 'Agua de jamaica', price: '45,50', description: '' }, null);
    expect(body.price).toBe(45.5);
    expect(JSON.parse(JSON.stringify(body))).toEqual({ name: 'Agua de jamaica', price: 45.5 });
  });

  it('incluye la descripción y la categoría cuando se capturan', () => {
    expect(toCreateProductRequest({ name: 'Flan', price: '38', description: 'Casero' }, 'c1')).toEqual({
      name: 'Flan',
      price: 38,
      description: 'Casero',
      categoryId: 'c1',
    });
  });
});
