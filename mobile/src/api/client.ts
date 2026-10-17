import { API_URL, CONFIG_ERROR, REQUEST_TIMEOUT_MS } from '../config';
import type { ProblemDetail } from './types';

/**
 * Cliente HTTP único de la app. Toda petición pasa por aquí para que el
 * transporte, el tiempo límite, la renovación de sesión y la traducción de
 * errores sean iguales en todas las pantallas.
 *
 * La sesión vive en cookies httpOnly que emite el backend. El almacén de
 * cookies nativo (OkHttp en Android, NSHTTPCookieStorage en iOS) las guarda y
 * las adjunta solo; JavaScript nunca lee ni guarda el token.
 */

export type ApiErrorKind =
  | 'config'
  | 'network'
  | 'timeout'
  | 'unauthorized'
  | 'forbidden'
  | 'validation'
  | 'notFound'
  | 'conflict'
  | 'throttled'
  | 'server';

export class ApiError extends Error {
  constructor(
    readonly kind: ApiErrorKind,
    message: string,
    readonly status: number | null = null,
    readonly fieldErrors: Record<string, string> = {},
  ) {
    super(message);
    this.name = 'ApiError';
  }
}

type Method = 'GET' | 'POST';

interface RequestOptions {
  body?: unknown;
  /** Las rutas de /auth no deben disparar la renovación automática. */
  skipRefresh?: boolean;
}

let refreshInFlight: Promise<boolean> | null = null;
let onSessionExpired: (() => void) | null = null;

/** La app registra aquí qué hacer cuando la sesión ya no se puede renovar. */
export function setSessionExpiredHandler(handler: (() => void) | null): void {
  onSessionExpired = handler;
}

export async function request<T>(method: Method, path: string, options: RequestOptions = {}): Promise<T> {
  const response = await send(method, path, options.body);

  // El access token dura 15 minutos. Ante un 401 se intenta una sola
  // renovación con el refresh token y se repite la petición original.
  if (response.status === 401 && !options.skipRefresh) {
    if (await refreshSession()) {
      return parse<T>(await send(method, path, options.body));
    }
    onSessionExpired?.();
  }
  return parse<T>(response);
}

async function send(method: Method, path: string, body: unknown): Promise<Response> {
  if (CONFIG_ERROR) {
    throw new ApiError('config', CONFIG_ERROR);
  }

  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MS);
  try {
    return await fetch(`${API_URL}/api/v1${path}`, {
      method,
      credentials: 'include',
      headers: {
        Accept: 'application/json',
        ...(body !== undefined ? { 'Content-Type': 'application/json' } : {}),
      },
      body: body !== undefined ? JSON.stringify(body) : undefined,
      signal: controller.signal,
    });
  } catch (cause) {
    if (controller.signal.aborted) {
      throw new ApiError('timeout', 'El servidor tardó demasiado en responder. Intenta de nuevo.');
    }
    throw new ApiError('network', 'No se pudo conectar con el servidor. Revisa tu conexión a internet.');
  } finally {
    clearTimeout(timer);
  }
}

function refreshSession(): Promise<boolean> {
  // Varias peticiones pueden recibir 401 a la vez; comparten una sola
  // renovación porque el backend rota el refresh token en cada uso.
  refreshInFlight ??= send('POST', '/auth/refresh', undefined)
    .then((response) => response.ok)
    .catch(() => false)
    .finally(() => {
      refreshInFlight = null;
    });
  return refreshInFlight;
}

async function parse<T>(response: Response): Promise<T> {
  if (response.ok) {
    if (response.status === 204) {
      return undefined as T;
    }
    return (await response.json()) as T;
  }
  throw await toApiError(response);
}

async function toApiError(response: Response): Promise<ApiError> {
  const problem = await readProblem(response);
  const detail = problem?.detail;
  const status = response.status;

  switch (status) {
    case 400:
      return new ApiError('validation', detail ?? 'Revisa los datos enviados.', status, problem?.errors ?? {});
    case 401:
      return new ApiError('unauthorized', 'Tu sesión expiró. Inicia sesión de nuevo.', status);
    case 403:
      return new ApiError('forbidden', 'Tu rol no tiene permiso para esta acción.', status);
    case 404:
      return new ApiError('notFound', detail ?? 'El recurso solicitado no existe.', status);
    case 409:
      return new ApiError('conflict', detail ?? 'La operación entra en conflicto con el estado actual.', status);
    case 429:
      return new ApiError('throttled', detail ?? 'Demasiados intentos. Espera unos minutos.', status);
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
