import type { ProblemDetail } from "./types";

const API_BASE_URL =
  process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080";

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
  get: <TResponse>(path: string, options?: RequestOptions) =>
    request<TResponse>(path, { ...options, method: "GET" }),
  post: <TResponse>(path: string, body?: unknown, options?: RequestOptions) =>
    request<TResponse>(path, { ...options, method: "POST", body }),
};
