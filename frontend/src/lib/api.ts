import { endpoints } from "./endpoints";
import type { ProblemDetail } from "./types";

/**
 * En el navegador la URL base es vacia (relativa): la peticion sale hacia
 * /api/v1/... y el rewrite de next.config.ts la reenvia al backend, sin
 * CORS y con la cookie de sesion como first-party. `fetch` del lado del
 * servidor (Server Components, Route Handlers) no acepta URLs relativas, asi
 * que ahi se necesita el origen absoluto del backend.
 */
const API_BASE_URL =
  typeof window === "undefined"
    ? process.env.BACKEND_ORIGIN ?? "http://localhost:8080"
    : "";

/**
 * Error tipado que envuelve un RFC 7807 ProblemDetail. El mensaje que
 * transporta es siempre `detail`, para que quien capture el error pueda
 * mostrarlo directamente al usuario.
 */
export class ApiError extends Error {
  readonly status: number;
  readonly title?: string;
  readonly errors?: Record<string, string>;

  constructor(problem: ProblemDetail, status: number) {
    super(problem.detail ?? "Ocurrio un error al comunicarse con el servidor.");
    this.name = "ApiError";
    this.status = status;
    this.title = problem.title;
    this.errors = problem.errors;
  }
}

type RequestOptions = Omit<RequestInit, "body"> & {
  body?: unknown;
};

/** Valores admitidos como parametros de query string en `api.get`. */
type QueryParams = Record<string, string | number | boolean | undefined | null>;

/** Omite los valores `undefined`/`null` para no mandar `?foo=undefined`. */
function buildQueryString(params?: QueryParams): string {
  if (!params) {
    return "";
  }

  const searchParams = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value === undefined || value === null) {
      continue;
    }
    searchParams.set(key, String(value));
  }

  const query = searchParams.toString();
  return query ? `?${query}` : "";
}

/**
 * Rutas de sesion cuyo 401 es la respuesta definitiva: renovar ahi no tiene
 * sentido (login con credenciales malas, logout) o seria recursivo (refresh).
 */
const SESSION_PATHS = new Set([
  endpoints.auth.login(),
  endpoints.auth.logout(),
  endpoints.auth.refresh(),
]);

/** Nombre del Web Lock que serializa la renovacion entre pestanas. */
const REFRESH_LOCK = "callejon9:auth-refresh";

let refreshInFlight: Promise<boolean> | null = null;

/**
 * Renueva la sesion con POST /auth/refresh. Las peticiones que reciben 401 a
 * la vez comparten una sola renovacion: el backend trata un refresh token
 * presentado dos veces como robado y revoca la sesion entera, asi que dos
 * renovaciones paralelas con la misma cookie cerrarian la sesion del usuario.
 *
 * Por la misma razon, entre pestanas se serializa con Web Locks: la cookie es
 * compartida, y la pestana que espera el candado ya envia la cookie nueva que
 * dejo la primera.
 */
function refreshSession(): Promise<boolean> {
  refreshInFlight ??= runRefresh().finally(() => {
    refreshInFlight = null;
  });
  return refreshInFlight;
}

async function runRefresh(): Promise<boolean> {
  const call = () =>
    fetch(`${API_BASE_URL}${endpoints.auth.refresh()}`, {
      method: "POST",
      credentials: "include",
    })
      .then((response) => response.ok)
      .catch(() => false);

  if (typeof navigator !== "undefined" && navigator.locks) {
    // `await` aplana el tipo: la definicion de lib.dom envuelve dos veces la
    // promesa que devuelve el callback.
    return await navigator.locks.request(REFRESH_LOCK, call);
  }
  return call();
}

/**
 * Solo el navegador renueva: ahi viven las cookies que el backend reemplaza.
 * Un fetch del lado del servidor no podria entregarle las cookies nuevas.
 */
function canRefresh(path: string): boolean {
  return typeof window !== "undefined" && !SESSION_PATHS.has(path.split("?")[0]);
}

/**
 * Envoltorio delgado sobre fetch. Siempre envia `credentials: 'include'`
 * porque el backend autentica con una cookie httpOnly (`access_token`); el
 * token nunca se lee ni se guarda en JavaScript.
 *
 * Ante un 401 renueva la sesion una sola vez y repite la peticion original.
 * Para quien llama es transparente: la mutation o query simplemente termina
 * bien, sin desmontar el formulario. Si la renovacion falla, el 401 original
 * sigue su curso hacia el manejador global de `providers.tsx`.
 */
async function send(
  path: string,
  options: RequestOptions = {},
  allowRefresh = true,
): Promise<Response> {
  const { body, headers, ...rest } = options;
  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...rest,
    credentials: "include",
    headers: {
      "Content-Type": "application/json",
      ...headers,
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  });

  if (
    response.status === 401 &&
    allowRefresh &&
    canRefresh(path) &&
    (await refreshSession())
  ) {
    return send(path, options, false);
  }

  return response;
}

async function request<TResponse>(
  path: string,
  options: RequestOptions = {},
): Promise<TResponse> {
  const response = await send(path, options);

  if (!response.ok) {
    throw new ApiError(await parseProblemDetail(response), response.status);
  }

  if (response.status === 204) {
    return undefined as TResponse;
  }

  return (await response.json()) as TResponse;
}

async function parseProblemDetail(response: Response): Promise<ProblemDetail> {
  try {
    return (await response.json()) as ProblemDetail;
  } catch {
    return { title: response.statusText, status: response.status };
  }
}

export const api = {
  get: <TResponse>(
    path: string,
    params?: QueryParams,
    options?: RequestOptions,
  ) => request<TResponse>(`${path}${buildQueryString(params)}`, { ...options, method: "GET" }),
  post: <TResponse>(path: string, body?: unknown, options?: RequestOptions) =>
    request<TResponse>(path, { ...options, method: "POST", body }),
  put: <TResponse>(path: string, body?: unknown, options?: RequestOptions) =>
    request<TResponse>(path, { ...options, method: "PUT", body }),
  patch: <TResponse>(path: string, body?: unknown, options?: RequestOptions) =>
    request<TResponse>(path, { ...options, method: "PATCH", body }),
  del: <TResponse>(path: string, options?: RequestOptions) =>
    request<TResponse>(path, { ...options, method: "DELETE" }),
  /** Descarga binaria (PDF) con la misma cookie y la misma renovacion. */
  blob: async (path: string, options?: RequestOptions): Promise<Blob> => {
    const response = await send(path, { ...options, method: "GET" });
    if (!response.ok) {
      throw new ApiError(await parseProblemDetail(response), response.status);
    }
    return response.blob();
  },
};
