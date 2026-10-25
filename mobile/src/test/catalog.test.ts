import { afterEach, describe, expect, it, vi } from 'vitest';

const API = 'https://api.ejemplo.mx';

async function loadCatalog(fetchMock: ReturnType<typeof vi.fn>) {
  vi.resetModules();
  vi.stubEnv('EXPO_PUBLIC_API_URL', API);
  vi.stubGlobal('fetch', fetchMock);
  const catalog = await import('../api/catalog');
  const waiter = await import('../api/waiter');
  const client = await import('../api/client');
  return { ...catalog, ...waiter, ...client };
}

const json = (status: number, body: unknown, contentType = 'application/json') =>
  new Response(JSON.stringify(body), { status, headers: { 'Content-Type': contentType } });
const problem = (status: number, extra: Record<string, unknown> = {}) =>
  json(status, { type: 'about:blank', status, ...extra }, 'application/problem+json');

const product = { id: 'p1', name: 'Agua de jamaica', description: null, price: 45.5, categoryId: 'c1', active: true };
const body = { name: 'Agua de jamaica', price: 45.5, categoryId: 'c1' };

afterEach(() => {
  vi.resetModules();
});

describe('catálogo: consulta', () => {
  it('GET /products y GET /categories devuelven el JSON del servidor', async () => {
    const categories = [{ id: 'c1', name: 'Bebidas', sortOrder: 1 }];
    const fetchMock = vi.fn().mockResolvedValueOnce(json(200, [product])).mockResolvedValueOnce(json(200, categories));
    const { fetchProducts, fetchCategories } = await loadCatalog(fetchMock);

    await expect(fetchProducts()).resolves.toEqual([product]);
    await expect(fetchCategories()).resolves.toEqual(categories);
    expect(fetchMock.mock.calls.map(([url, init]) => [init.method, url])).toEqual([
      ['GET', `${API}/api/v1/products`],
      ['GET', `${API}/api/v1/categories`],
    ]);
  });
});

describe('catálogo: registro de producto', () => {
  it('envía POST /products con el cuerpo en JSON y devuelve el producto creado (201)', async () => {
    const fetchMock = vi.fn().mockResolvedValue(json(201, product));
    const { createProduct } = await loadCatalog(fetchMock);

    await expect(createProduct(body)).resolves.toEqual(product);
    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe(`${API}/api/v1/products`);
    expect(init.method).toBe('POST');
    expect(init.credentials).toBe('include');
    expect(init.headers['Content-Type']).toBe('application/json');
    expect(JSON.parse(init.body)).toEqual(body);
  });

  it('un 400 del servidor llega con el aviso de cada campo', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      problem(400, { detail: 'La solicitud contiene campos invalidos.', errors: { name: 'no debe estar vacío' } }),
    );
    const { createProduct } = await loadCatalog(fetchMock);

    await expect(createProduct({ ...body, name: '' })).rejects.toMatchObject({
      kind: 'validation',
      status: 400,
      fieldErrors: { name: 'no debe estar vacío' },
    });
  });

  it('un 403 (rol sin permiso) muestra un mensaje fijo, no el detalle del servidor', async () => {
    const fetchMock = vi.fn().mockResolvedValue(problem(403, { detail: 'Access Denied' }));
    const { createProduct } = await loadCatalog(fetchMock);

    await expect(createProduct(body)).rejects.toMatchObject({
      kind: 'forbidden',
      message: 'Tu rol no tiene permiso para esta acción.',
    });
  });

  it('un 500 no expone el error interno', async () => {
    const fetchMock = vi.fn().mockResolvedValue(problem(500, { detail: 'org.postgresql.util.PSQLException: ...' }));
    const { createProduct } = await loadCatalog(fetchMock);

    const error = (await createProduct(body).catch((e: unknown) => e)) as Error;
    expect(error).toMatchObject({ kind: 'server', message: 'El servidor tuvo un problema. Intenta más tarde.' });
    expect(error.message).not.toContain('PSQL');
  });

  it('sin conexión, el registro falla con el aviso de red', async () => {
    const fetchMock = vi.fn().mockRejectedValue(new TypeError('Network request failed'));
    const { createProduct } = await loadCatalog(fetchMock);

    await expect(createProduct(body)).rejects.toMatchObject({
      kind: 'network',
      message: 'No se pudo conectar con el servidor. Revisa tu conexión a internet.',
    });
  });

  it('con la sesión caducada, renueva una vez y repite el mismo POST', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(new Response(null, { status: 401 }))
      .mockResolvedValueOnce(new Response(null, { status: 204 }))
      .mockResolvedValueOnce(json(201, product));
    const { createProduct } = await loadCatalog(fetchMock);

    await expect(createProduct(body)).resolves.toEqual(product);
    const calls = fetchMock.mock.calls.map(([url, init]) => [init.method, url.replace(API, ''), init.body]);
    expect(calls).toEqual([
      ['POST', '/api/v1/products', JSON.stringify(body)],
      ['POST', '/api/v1/auth/refresh', undefined],
      ['POST', '/api/v1/products', JSON.stringify(body)],
    ]);
  });
});
