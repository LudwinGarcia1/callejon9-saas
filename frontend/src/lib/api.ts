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
 * Envoltorio delgado sobre fetch. Siempre envia `credentials: 'include'`
 * porque el backend autentica con una cookie httpOnly (`access_token`); el
 * token nunca se lee ni se guarda en JavaScript.
 */
async function request<TResponse>(
  path: string,
  { body, headers, ...rest }: RequestOptions = {},
): Promise<TResponse> {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...rest,
    credentials: "include",
    headers: {
      "Content-Type": "application/json",
      ...headers,
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  });

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
};
