import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

type FetchMock = ReturnType<typeof vi.fn>;

/**
 * `api.ts` decide si corre en el navegador al cargarse, asi que cada prueba
 * lo importa de nuevo con `window` ya definido.
 */
async function loadApi() {
  vi.resetModules();
  vi.stubGlobal("window", {});
  return import("../lib/api");
}

function jsonResponse(status: number, body?: unknown): Response {
  return new Response(body === undefined ? null : JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

function calledPaths(fetchMock: FetchMock): string[] {
  return fetchMock.mock.calls.map(([url]) => String(url));
}

describe("renovacion transparente de la sesion", () => {
  let fetchMock: FetchMock;

  beforeEach(() => {
    fetchMock = vi.fn();
    vi.stubGlobal("fetch", fetchMock);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("ante un 401 renueva una vez y reintenta la peticion original con el mismo cuerpo", async () => {
    fetchMock
      .mockResolvedValueOnce(jsonResponse(401))
      .mockResolvedValueOnce(new Response(null, { status: 204 }))
      .mockResolvedValueOnce(jsonResponse(201, { id: "orden-1" }));
    const { api } = await loadApi();

    const result = await api.post<{ id: string }>("/api/v1/orders", { tableId: "mesa-1" });

    expect(result).toEqual({ id: "orden-1" });
    expect(calledPaths(fetchMock)).toEqual([
      "/api/v1/orders",
      "/api/v1/auth/refresh",
      "/api/v1/orders",
    ]);
    expect(fetchMock.mock.calls[2][1]).toMatchObject({
      method: "POST",
      credentials: "include",
      body: JSON.stringify({ tableId: "mesa-1" }),
    });
  });

  it("si la renovacion falla, propaga el 401 original sin reintentar", async () => {
    fetchMock
      .mockResolvedValueOnce(jsonResponse(401))
      .mockResolvedValueOnce(new Response(null, { status: 401 }));
    const { api, ApiError } = await loadApi();

    const error = await api.get("/api/v1/tables").catch((caught: unknown) => caught);

    expect(error).toBeInstanceOf(ApiError);
    expect((error as InstanceType<typeof ApiError>).status).toBe(401);
    expect(calledPaths(fetchMock)).toEqual(["/api/v1/tables", "/api/v1/auth/refresh"]);
  });

  it("no entra en bucle si la peticion reintentada vuelve a dar 401", async () => {
    fetchMock
      .mockResolvedValueOnce(jsonResponse(401))
      .mockResolvedValueOnce(new Response(null, { status: 204 }))
      .mockResolvedValueOnce(jsonResponse(401));
    const { api } = await loadApi();

    await expect(api.get("/api/v1/tables")).rejects.toMatchObject({ status: 401 });
    expect(fetchMock).toHaveBeenCalledTimes(3);
  });

  it("varios 401 simultaneos comparten una sola renovacion", async () => {
    let refreshed = false;
    let releaseRefresh: () => void = () => {};
    fetchMock.mockImplementation((url: string) => {
      if (url === "/api/v1/auth/refresh") {
        return new Promise<Response>((resolve) => {
          releaseRefresh = () => {
            refreshed = true;
            resolve(new Response(null, { status: 204 }));
          };
        });
      }
      return Promise.resolve(refreshed ? jsonResponse(200, []) : jsonResponse(401));
    });
    const { api } = await loadApi();

    const pending = Promise.all([api.get("/api/v1/tables"), api.get("/api/v1/products")]);
    await vi.waitFor(() =>
      expect(calledPaths(fetchMock).filter((path) => path === "/api/v1/auth/refresh")).toHaveLength(1),
    );
    releaseRefresh();

    await expect(pending).resolves.toEqual([[], []]);
    expect(calledPaths(fetchMock).filter((path) => path === "/api/v1/auth/refresh")).toHaveLength(1);
  });

  it("la descarga binaria tambien renueva y reintenta", async () => {
    fetchMock
      .mockResolvedValueOnce(jsonResponse(401))
      .mockResolvedValueOnce(new Response(null, { status: 204 }))
      .mockResolvedValueOnce(new Response("%PDF-1.7", { status: 200 }));
    const { api } = await loadApi();

    const blob = await api.blob("/api/v1/tickets/t-1/pdf");

    expect(await blob.text()).toBe("%PDF-1.7");
    expect(calledPaths(fetchMock)).toEqual([
      "/api/v1/tickets/t-1/pdf",
      "/api/v1/auth/refresh",
      "/api/v1/tickets/t-1/pdf",
    ]);
  });

  it.each(["/api/v1/auth/login", "/api/v1/auth/logout", "/api/v1/auth/refresh"])(
    "un 401 de %s no dispara renovacion",
    async (path) => {
      fetchMock.mockResolvedValueOnce(jsonResponse(401));
      const { api } = await loadApi();

      await expect(api.post(path)).rejects.toMatchObject({ status: 401 });
      expect(fetchMock).toHaveBeenCalledTimes(1);
    },
  );
});
