import { describe, expect, it } from "vitest";

import { isProtectedPagePath, loginPathFor } from "../lib/protected-pages";

describe("rutas privadas", () => {
  it.each([
    "/admin",
    "/analytics",
    "/cashier",
    "/history",
    "/inventory",
    "/kitchen",
    "/platform",
    "/waiter/order/123",
  ])("marca %s como ruta protegida", (pathname) => {
    expect(isProtectedPagePath(pathname)).toBe(true);
  });

  it.each(["/login", "/signup", "/api/v1/orders", "/favicon.ico"])(
    "no marca %s como ruta protegida",
    (pathname) => {
      expect(isProtectedPagePath(pathname)).toBe(false);
    },
  );

  it("preserva una ruta interna codificada al redirigir a login", () => {
    expect(loginPathFor("/waiter/order/a?source=table")).toBe(
      "/login?next=%2Fwaiter%2Forder%2Fa%3Fsource%3Dtable",
    );
  });
});
