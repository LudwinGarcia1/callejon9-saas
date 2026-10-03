import { afterEach, describe, expect, it, vi } from "vitest";
import { NextRequest } from "next/server";
import { middleware } from "../middleware";
import { contentSecurityPolicy } from "../lib/security-headers";
import nextConfig from "../../next.config";

afterEach(() => vi.unstubAllEnvs());

describe("cabeceras de seguridad", () => {
  it("permite scripts con nonce y bloquea eval, objetos e iframes en produccion", () => {
    const csp = contentSecurityPolicy("randomNonce", false);
    expect(csp).toContain("script-src 'self' 'nonce-randomNonce' 'strict-dynamic'");
    expect(csp).not.toContain("unsafe-eval");
    expect(csp.split(";").map((directive) => directive.trim()).find((directive) => directive.startsWith("script-src"))).not.toContain("unsafe-inline");
    expect(csp).toContain("frame-ancestors 'none'");
    expect(csp).toContain("object-src 'none'");
    expect(csp).toContain("form-action 'self'");
  });

  it("limita eval y conexiones HMR al desarrollo", () => {
    expect(contentSecurityPolicy("nonce", true)).toContain("'unsafe-eval'");
    expect(contentSecurityPolicy("nonce", true)).toContain("connect-src 'self' ws: wss:");
    expect(contentSecurityPolicy("nonce", false)).toContain("connect-src 'self';");
  });

  it.each(["/login", "/signup", "/missing-page"])("protege %s sin exigir sesion", (path) => {
    const response = middleware(new NextRequest(`http://localhost${path}`));
    expect(response.status).toBe(200);
    expect(response.headers.get("Content-Security-Policy")).toContain("frame-ancestors 'none'");
  });

  it("genera un nonce distinto e ignora cabeceras CSP suministradas por el cliente", () => {
    const request = new NextRequest("http://localhost/login", {
      headers: { "Content-Security-Policy": "script-src *", "x-nonce": "attacker" },
    });
    const first = middleware(request);
    const second = middleware(request);
    const csp = first.headers.get("Content-Security-Policy");
    expect(csp).not.toBe(second.headers.get("Content-Security-Policy"));
    const nonce = first.headers.get("x-middleware-request-x-nonce");
    expect(nonce).toMatch(/^[A-Za-z0-9+/]{22}==$/);
    expect(csp).toContain(`'nonce-${nonce}'`);
    expect(first.headers.get("x-middleware-request-content-security-policy")).toBe(csp);
  });

  it("mantiene la redireccion de rutas protegidas con CSP", () => {
    const response = middleware(new NextRequest("http://localhost/kitchen"));
    expect(response.status).toBe(307);
    expect(response.headers.get("location")).toBe("http://localhost/login?next=%2Fkitchen");
    expect(response.headers.get("Content-Security-Policy")).toContain("frame-ancestors 'none'");
  });

  it("permite paginas protegidas con cookie y deja pasar la API sin redireccion", () => {
    const authenticated = middleware(new NextRequest("http://localhost/kitchen", {
      headers: { cookie: "access_token=test" },
    }));
    expect(authenticated.status).toBe(200);
    expect(authenticated.headers.get("x-middleware-request-x-pathname")).toBe("/kitchen");
    const api = middleware(new NextRequest("http://localhost/api/v1/auth/login"));
    expect(api.status).toBe(200);
    expect(api.headers.get("location")).toBeNull();
    expect(api.headers.get("Content-Security-Policy")).toBeNull();
  });

  it("envia proteccion comun y HSTS de produccion, con opcion local de desactivarlo", async () => {
    vi.stubEnv("NODE_ENV", "production");
    vi.stubEnv("SECURITY_HSTS_ENABLED", "true");
    const rules = await nextConfig.headers!();
    expect(rules[0].headers).toEqual(expect.arrayContaining([
      { key: "X-Frame-Options", value: "DENY" },
      { key: "X-Content-Type-Options", value: "nosniff" },
      { key: "Referrer-Policy", value: "strict-origin-when-cross-origin" },
      { key: "Strict-Transport-Security", value: "max-age=31536000" },
    ]));
    vi.stubEnv("SECURITY_HSTS_ENABLED", "false");
    expect((await nextConfig.headers!())[0].headers.some((header) => header.key === "Strict-Transport-Security")).toBe(false);
    vi.stubEnv("NODE_ENV", "development");
    vi.stubEnv("SECURITY_HSTS_ENABLED", "true");
    expect((await nextConfig.headers!())[0].headers.some((header) => header.key === "Strict-Transport-Security")).toBe(false);
  });
});
