import { afterEach, describe, expect, it, vi } from 'vitest';
import { validateProduct } from '../validation/product';

/**
 * Sincronización del catálogo con el servicio remoto: consulta (GET),
 * registro (POST), actualización (PUT/PATCH) y respuestas fallidas. El
 * servidor se simula con un fetch falso; la prueba contra la API real en
 * Azure está en docs/pruebas/integracion-movil-nube/README.md.
 */

const API = 'https://api.ejemplo.mx';

type Catalog = typeof import('../api/catalog');

async function loadCatalog(fetchMock: ReturnType<typeof vi.fn>): Promise<Catalog> {
  vi.resetModules();
  vi.stubEnv('EXPO_PUBLIC_API_URL', API);
  vi.stubGlobal('fetch', fetchMock);
  return import('../api/catalog');
}

const json = (status: number, body: unknown, contentType = 'application/json') =>
  new Response(JSON.stringify(body), { status, headers: { 'Content-Type': contentType } });
const problem = (status: number, extra: Record<string, unknown> = {}) =>
  json(status, { type: 'about:blank', status, ...extra }, 'application/problem+json');

const taco = {
  id: 'p1',
  name: 'Taco de bistec',
  description: null,
  price: 45,
  categoryId: 'c1',
  active: true,
};

afterEach(() => {
  vi.useRealTimers();
  vi.resetModules();
});

describe('catalog: consulta', () => {
  it('pide solo los productos activos cuando no se piden los inactivos', async () => {
    const fetchMock = vi.fn().mockResolvedValue(json(200, [taco]));
    const { fetchCatalog } = await loadCatalog(fetchMock);

    await expect(fetchCatalog(false)).resolves.toEqual([taco]);
    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe(`${API}/api/v1/products`);
    expect(init.method).toBe('GET');
    expect(init.credentials).toBe('include');
  });

  it('para ADMIN incluye los inactivos, para poder reactivarlos', async () => {
    const fetchMock = vi.fn().mockResolvedValue(json(200, []));
    const { fetchCatalog } = await loadCatalog(fetchMock);

    await fetchCatalog(true);
    expect(fetchMock.mock.calls[0][0]).toBe(`${API}/api/v1/products?includeInactive=true`);
  });

  it('no envía el restaurante: el backend lo toma del JWT firmado', async () => {
    const fetchMock = vi.fn().mockResolvedValue(json(200, []));
    const { fetchCatalog } = await loadCatalog(fetchMock);

    await fetchCatalog(true);
    const [url, init] = fetchMock.mock.calls[0];
    expect(url).not.toMatch(/tenant|slug/i);
    expect(init.body).toBeUndefined();
  });
});

describe('catalog: registro', () => {
  it('envía POST /products con el cuerpo en JSON y devuelve el producto creado', async () => {
    const fetchMock = vi.fn().mockResolvedValue(json(201, taco));
    const { createProduct } = await loadCatalog(fetchMock);

    await expect(createProduct({ name: 'Taco de bistec', price: 45, categoryId: 'c1' })).resolves.toEqual(taco);
    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe(`${API}/api/v1/products`);
    expect(init.method).toBe('POST');
    expect(init.headers['Content-Type']).toBe('application/json');
    expect(JSON.parse(init.body)).toEqual({ name: 'Taco de bistec', price: 45, categoryId: 'c1' });
  });

  it('convierte un 400 del servidor en errores por campo', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      problem(400, { detail: 'La solicitud tiene datos inválidos.', errors: { name: 'no debe estar vacío' } }),
    );
    const { createProduct } = await loadCatalog(fetchMock);

    await expect(createProduct({ name: '', price: 45 })).rejects.toMatchObject({
      kind: 'validation',
      status: 400,
      fieldErrors: { name: 'no debe estar vacío' },
    });
  });

  it('un rol sin permiso recibe 403 y un mensaje sin detalles internos', async () => {
    const fetchMock = vi.fn().mockResolvedValue(problem(403, { detail: 'Access Denied' }));
    const { createProduct } = await loadCatalog(fetchMock);

    const error = await createProduct({ name: 'Taco', price: 45 }).catch((cause: unknown) => cause);
    expect(error).toMatchObject({ kind: 'forbidden', status: 403 });
    expect((error as Error).message).toBe('Tu rol no tiene permiso para esta acción.');
  });
});

describe('catalog: actualización', () => {
  it('envía PUT /products/{id} con todos los campos', async () => {
    const updated = { ...taco, price: 50 };
    const fetchMock = vi.fn().mockResolvedValue(json(200, updated));
    const { updateProduct } = await loadCatalog(fetchMock);

    await expect(updateProduct('p1', { name: 'Taco de bistec', price: 50, categoryId: 'c1' })).resolves.toEqual(
      updated,
    );
    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe(`${API}/api/v1/products/p1`);
    expect(init.method).toBe('PUT');
    expect(JSON.parse(init.body)).toEqual({ name: 'Taco de bistec', price: 50, categoryId: 'c1' });
  });

  it('envía PATCH /products/{id} solo con el estado', async () => {
    const fetchMock = vi.fn().mockResolvedValue(json(200, { ...taco, active: false }));
    const { setProductActive } = await loadCatalog(fetchMock);

    await expect(setProductActive('p1', false)).resolves.toMatchObject({ active: false });
    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe(`${API}/api/v1/products/p1`);
    expect(init.method).toBe('PATCH');
    expect(JSON.parse(init.body)).toEqual({ active: false });
  });

  it('codifica el id para que no pueda alterar la ruta', async () => {
    const fetchMock = vi.fn().mockResolvedValue(json(200, taco));
    const { setProductActive } = await loadCatalog(fetchMock);

    await setProductActive('../users', true);
    expect(fetchMock.mock.calls[0][0]).toBe(`${API}/api/v1/products/..%2Fusers`);
  });

  it('muestra el motivo de un 409, como un nombre repetido', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      problem(409, { detail: "Ya existe un producto llamado 'Taco de bistec'." }),
    );
    const { updateProduct } = await loadCatalog(fetchMock);

    await expect(updateProduct('p2', { name: 'Taco de bistec', price: 45 })).rejects.toMatchObject({
      kind: 'conflict',
      message: "Ya existe un producto llamado 'Taco de bistec'.",
    });
  });

  it('un 404 indica que el producto ya no existe', async () => {
    const fetchMock = vi.fn().mockResolvedValue(problem(404, { detail: 'El producto no existe.' }));
    const { setProductActive } = await loadCatalog(fetchMock);

    await expect(setProductActive('p9', true)).rejects.toMatchObject({ kind: 'notFound', status: 404 });
  });
});

describe('catalog: fallas de conexión y respuestas fallidas', () => {
  it('sin red, avisa que no hubo conexión', async () => {
    const fetchMock = vi.fn().mockRejectedValue(new TypeError('Network request failed'));
    const { createProduct } = await loadCatalog(fetchMock);

    await expect(createProduct({ name: 'Taco', price: 45 })).rejects.toMatchObject({
      kind: 'network',
      message: 'No se pudo conectar con el servidor. Revisa tu conexión a internet.',
    });
  });

  it('si el servidor no responde a tiempo, corta la petición y lo avisa', async () => {
    vi.useFakeTimers();
    const fetchMock = vi.fn().mockImplementation(
      (_url: string, init: RequestInit) =>
        new Promise((_resolve, reject) => {
          init.signal?.addEventListener('abort', () => reject(new DOMException('Aborted', 'AbortError')));
        }),
    );
    const { fetchCatalog } = await loadCatalog(fetchMock);

    const pending = fetchCatalog(false).catch((cause: unknown) => cause);
    await vi.runAllTimersAsync();
    expect(await pending).toMatchObject({ kind: 'timeout' });
  });

  it('un 500 se muestra con un mensaje genérico, sin el detalle interno', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      problem(500, { detail: 'org.postgresql.util.PSQLException: password authentication failed' }),
    );
    const { updateProduct } = await loadCatalog(fetchMock);

    const error = await updateProduct('p1', { name: 'Taco', price: 45 }).catch((cause: unknown) => cause);
    expect(error).toMatchObject({ kind: 'server', status: 500 });
    expect((error as Error).message).toBe('El servidor tuvo un problema. Intenta más tarde.');
    expect((error as Error).message).not.toMatch(/PSQL|password/);
  });
});

describe('validación antes del envío', () => {
  it('acepta un producto completo', () => {
    expect(validateProduct('Taco de bistec', '45.50', '')).toEqual({});
    expect(validateProduct('Taco de bistec', '45,5', 'Con cebolla')).toEqual({});
  });

  it('rechaza nombre y precio vacíos', () => {
    expect(validateProduct('', '', '')).toEqual({
      name: 'El nombre es obligatorio.',
      price: 'El precio es obligatorio.',
    });
  });

  it('rechaza precios negativos, con texto o con más de dos decimales', () => {
    for (const price of ['-5', 'abc', '45.505', '123456789']) {
      expect(validateProduct('Taco', price, '').price).toBeDefined();
    }
  });

  it('rechaza textos más largos que los límites (nombre: varchar(160); descripción: tope del cliente)', () => {
    const errors = validateProduct('x'.repeat(161), '45', 'y'.repeat(501));
    expect(errors.name).toBe('Máximo 160 caracteres.');
    expect(errors.description).toBe('Máximo 500 caracteres.');
  });
});
