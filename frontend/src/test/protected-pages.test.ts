import { describe, expect, it } from "vitest";
import { NextRequest } from "next/server";

import { config, middleware } from "../middleware";
import {
  isProtectedPagePath,
  loginPathFor,
  PROTECTED_PAGE_PREFIXES,
} from "../lib/protected-pages";

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

  // El matcher cubre todo salvo los assets para que cada pagina reciba su
  // nonce de CSP; lo que importa es que ninguna ruta operativa quede fuera.
  const matchesMiddleware = (pathname: string) =>
    config.matcher.some((pattern) => new RegExp(`^${pattern}$`).test(pathname));

  it.each(PROTECTED_PAGE_PREFIXES.flatMap((prefix) => [prefix, `${prefix}/detalle`]))(
    "el middleware intercepta la ruta operativa %s",
    (pathname) => {
      expect(matchesMiddleware(pathname)).toBe(true);
    },
  );

  it.each(["/_next/static/chunk.js", "/_next/image", "/favicon.ico"])(
    "el middleware no intercepta el asset %s",
    (pathname) => {
      expect(matchesMiddleware(pathname)).toBe(false);
    },
  );

  it.each(["/history", "/analytics", "/inventory", "/waiter/order/123"])(
    "redirige %s a login cuando no hay cookie",
    (pathname) => {
      const response = middleware(new NextRequest(`https://callejon9.test${pathname}`));

      expect(response.headers.get("location")).toBe(
        `https://callejon9.test/login?next=${encodeURIComponent(pathname)}`,
      );
    },
  );

  it("solo usa la presencia de cookie como pista de navegacion", () => {
    const response = middleware(
      new NextRequest("https://callejon9.test/history", {
        headers: { cookie: "access_token=not-a-validated-jwt" },
      }),
    );

    expect(response.headers.get("x-middleware-next")).toBe("1");
  });

  it("preserva una ruta interna codificada al redirigir a login", () => {
    expect(loginPathFor("/waiter/order/a?source=table")).toBe(
      "/login?next=%2Fwaiter%2Forder%2Fa%3Fsource%3Dtable",
    );
  });
});
