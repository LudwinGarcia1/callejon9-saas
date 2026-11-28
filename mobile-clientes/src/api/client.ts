import { API_URL, CONFIG_ERROR, REQUEST_TIMEOUT_MS } from '../config';
import type { ProblemDetail } from './types';

/**
 * Cliente HTTP de la app de comensales. Solo habla con las rutas públicas
 * /api/v1/public/*: no hay sesión, cookies ni datos personales. El restaurante
 * se identifica por su slug y el backend lo resuelve y aplica RLS.
 */

export type ApiErrorKind = 'config' | 'network' | 'timeout' | 'validation' | 'notFound' | 'conflict' | 'throttled' | 'server';

export class ApiError extends Error {
  constructor(
    readonly kind: ApiErrorKind,
    message: string,
    readonly status: number | null = null,
  ) {
    super(message);
    this.name = 'ApiError';
  }
}

/**
 * La petición no llegó al servidor: se puede repetir sin riesgo de duplicar.
 * Un timeout no entra aquí, porque el servidor pudo haberla procesado.
 */
export function isOfflineError(error: unknown): boolean {
  return error instanceof ApiError && error.kind === 'network';
}

export async function request<T>(method: 'GET' | 'POST', path: string, body?: unknown): Promise<T> {
  if (CONFIG_ERROR) {
    throw new ApiError('config', CONFIG_ERROR);
  }

  const controller = new AbortController();
  let timer: ReturnType<typeof setTimeout> | undefined;
  // El tiempo límite no depende de que fetch respete el abort: en Android,
  // con la red caída a medias, la petición puede quedarse colgada.
  const deadline = new Promise<never>((_, reject) => {
    timer = setTimeout(() => {
      controller.abort();
      reject(new ApiError('timeout', 'El servidor tardó demasiado en responder.'));
    }, REQUEST_TIMEOUT_MS);
  });
  let response: Response;
  try {
    response = await Promise.race([
      fetch(`${API_URL}/api/v1${path}`, {
        method,
        credentials: 'omit',
        headers: {
          Accept: 'application/json',
          ...(body !== undefined ? { 'Content-Type': 'application/json' } : {}),
        },
        body: body !== undefined ? JSON.stringify(body) : undefined,
        signal: controller.signal,
      }),
      deadline,
    ]);
  } catch (error) {
    if (error instanceof ApiError || controller.signal.aborted) {
      throw new ApiError('timeout', 'El servidor tardó demasiado en responder.');
    }
    throw new ApiError('network', 'Sin conexión con el servidor.');
  } finally {
    clearTimeout(timer);
  }

  if (response.ok) {
    return (response.status === 204 ? undefined : await response.json()) as T;
  }
  throw await toApiError(response);
}

async function toApiError(response: Response): Promise<ApiError> {
  const detail = (await readProblem(response))?.detail;
  const status = response.status;
  switch (status) {
    case 400:
      return new ApiError('validation', detail ?? 'Revisa el pedido.', status);
    case 404:
      return new ApiError('notFound', detail ?? 'No encontramos el restaurante o la mesa.', status);
    case 409:
    case 422:
      return new ApiError('conflict', detail ?? 'El pedido ya no se puede enviar así.', status);
    case 429:
      return new ApiError('throttled', detail ?? 'Demasiadas peticiones. Espera un momento.', status);
    default:
      return new ApiError('server', 'El servidor tuvo un problema. Intenta más tarde.', status);
  }
}

async function readProblem(response: Response): Promise<ProblemDetail | null> {
  try {
    const text = await response.text();
    return text ? (JSON.parse(text) as ProblemDetail) : null;
  } catch {
    return null;
  }
}
