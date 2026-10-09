import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { api, ApiError } from "@/lib/api";

describe("HTTP API contract", () => {
  const fetchMock = vi.fn<typeof fetch>();

  beforeEach(() => vi.stubGlobal("fetch", fetchMock));
  afterEach(() => {
    fetchMock.mockReset();
    vi.unstubAllGlobals();
  });

  it("preserves RFC7807 detail, field errors and HTTP status", async () => {
    fetchMock.mockResolvedValue(new Response(JSON.stringify({
      title: "Validación", detail: "Revisa los campos", status: 400,
      errors: { email: "Correo inválido" },
    }), { status: 422 }));
    const error = await api.post("/api/v1/orders", {}).catch((error: unknown) => error);
    expect(error).toBeInstanceOf(ApiError);
    expect(error).toMatchObject({
      message: "Revisa los campos", title: "Validación", status: 422,
      errors: { email: "Correo inválido" },
    });
  });

  it("provides a usable error when the server returns non-JSON", async () => {
    fetchMock.mockResolvedValue(new Response("unavailable", {
      status: 503, statusText: "Service Unavailable",
    }));
    await expect(api.get("/api/v1/orders")).rejects.toMatchObject({
      status: 503, title: "Service Unavailable",
      message: "Ocurrio un error al comunicarse con el servidor.",
    });
  });

  it("sends cookies and serializes the request without storing a token", async () => {
    fetchMock.mockResolvedValue(new Response(null, { status: 204 }));
    await expect(api.patch("/api/v1/orders/order-1", { notes: "Sin sal" }))
      .resolves.toBeUndefined();
    expect(fetchMock).toHaveBeenCalledWith(expect.stringContaining("/api/v1/orders/order-1"),
      expect.objectContaining({ method: "PATCH", credentials: "include",
        body: JSON.stringify({ notes: "Sin sal" }),
        headers: { "Content-Type": "application/json" } }));
  });

  it("retains false and zero filters, encodes text and omits missing values", async () => {
    fetchMock.mockResolvedValue(Response.json([]));
    await api.get("/api/v1/orders", {
      active: false, page: 0, search: "mesa & caja", missing: undefined, absent: null,
    });
    const url = new URL(String(fetchMock.mock.calls[0][0]), "https://app.example");
    expect(Object.fromEntries(url.searchParams)).toEqual({
      active: "false", page: "0", search: "mesa & caja",
    });
  });

  it("propagates transport failure instead of reporting success", async () => {
    const failure = new TypeError("Network unavailable");
    fetchMock.mockRejectedValue(failure);
    await expect(api.post("/api/v1/orders", {})).rejects.toBe(failure);
  });
});
