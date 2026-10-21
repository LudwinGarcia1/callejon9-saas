import { describe, expect, it } from 'vitest';
import { ApiError } from '../api/client';
import { REQUEST_TIMEOUT_MS } from '../config';

describe('mobile/src/api/client (Mapeo de errores y reglas de cliente)', () => {
  it('instancia ApiError correctamente con su tipo y código HTTP', () => {
    const error400 = new ApiError('validation', 'Datos inválidos', 400, { email: 'inválido' });
    expect(error400.kind).toBe('validation');
    expect(error400.status).toBe(400);
    expect(error400.message).toBe('Datos inválidos');
    expect(error400.fieldErrors).toEqual({ email: 'inválido' });

    const error401 = new ApiError('unauthorized', 'Sesión expirada', 401);
    expect(error401.kind).toBe('unauthorized');
    expect(error401.status).toBe(401);

    const error403 = new ApiError('forbidden', 'No autorizado', 403);
    expect(error403.kind).toBe('forbidden');

    const error404 = new ApiError('notFound', 'Recurso no encontrado', 404);
    expect(error404.kind).toBe('notFound');

    const error409 = new ApiError('conflict', 'Conflicto de concurrencia', 409);
    expect(error409.kind).toBe('conflict');

    const error429 = new ApiError('throttled', 'Demasiadas peticiones', 429);
    expect(error429.kind).toBe('throttled');

    const error500 = new ApiError('server', 'Error interno del servidor', 500);
    expect(error500.kind).toBe('server');
  });

  it('el tiempo límite por defecto de las peticiones es de 15 segundos', () => {
    expect(REQUEST_TIMEOUT_MS).toBe(15_000);
  });
});
