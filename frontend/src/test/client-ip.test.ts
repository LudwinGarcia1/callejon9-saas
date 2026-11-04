import { afterEach, describe, expect, it, vi } from "vitest";
import { NextRequest } from "next/server";

import { middleware } from "../middleware";
import {
  CLIENT_IP_HEADER,
  PROXY_SECRET_HEADER,
  apiRequestHeaders,
  browserIpFromForwardedFor,
} from "../lib/client-ip";

const SECRET = "secreto-de-prueba-del-proxy-de-next-32b";

afterEach(() => vi.unstubAllEnvs());

/** Cabeceras que la respuesta del middleware pide aplicar a la peticion reenviada. */
function forwardedRequestHeaders(response: Response): Record<string, string | null> {
  const overridden = (response.headers.get("x-middleware-override-headers") ?? "").split(",");
  const read = (name: string) =>
    overridden.includes(name) ? response.headers.get(`x-middleware-request-${name}`) : null;
  return {
    clientIp: read(CLIENT_IP_HEADER),
    secret: read(PROXY_SECRET_HEADER),
    forwardedFor: read("x-forwarded-for"),
  };
}

function apiRequest(path: string, headers: Record<string, string>): NextRequest {
  return new NextRequest(`https://restohub.example${path}`, { method: "POST", headers });
}

describe("IP del navegador desde X-Forwarded-For", () => {
  it("toma la ultima entrada, la que agrego el front end de App Service, y quita el puerto", () => {
    expect(browserIpFromForwardedFor("203.0.113.7:51234")).toBe("203.0.113.7");
    expect(browserIpFromForwardedFor("6.6.6.6, 10.0.0.1, 203.0.113.7:51234")).toBe("203.0.113.7");
    expect(browserIpFromForwardedFor("[2001:db8::7]:443")).toBe("2001:db8::7");
    expect(browserIpFromForwardedFor(" 2001:db8::7 ")).toBe("2001:db8::7");
  });

  it.each([null, "", "evil.example", "203.0.113", "203.0.113.7, ", ":51234", "[]:443"])(
    "devuelve null si la ultima entrada no es una IP (%s)",
    (forwardedFor) => {
      expect(browserIpFromForwardedFor(forwardedFor)).toBeNull();
    },
  );
});

describe("cabeceras hacia la API (CAL-169)", () => {
  it("con secreto, reenvia la IP del navegador y el secreto", () => {
    const headers = apiRequestHeaders(new Headers({ "x-forwarded-for": "6.6.6.6, 203.0.113.7:51234" }), SECRET);

    expect(headers.get(CLIENT_IP_HEADER)).toBe("203.0.113.7");
    expect(headers.get(PROXY_SECRET_HEADER)).toBe(SECRET);
    // La cadena original sigue intacta para la ruta sin secreto.
    expect(headers.get("x-forwarded-for")).toBe("6.6.6.6, 203.0.113.7:51234");
  });

  it.each([undefined, "", "   "])("sin secreto (%s) no agrega nada", (secret) => {
    const headers = apiRequestHeaders(new Headers({ "x-forwarded-for": "203.0.113.7:51234" }), secret);

    expect(headers.has(CLIENT_IP_HEADER)).toBe(false);
    expect(headers.has(PROXY_SECRET_HEADER)).toBe(false);
  });

  it("siempre quita las cabeceras propias que mande el navegador", () => {
    const forged = new Headers({
      "x-forwarded-for": "evil.example",
      [CLIENT_IP_HEADER]: "6.6.6.6",
      [PROXY_SECRET_HEADER]: "adivinado",
    });

    for (const secret of [undefined, SECRET]) {
      const headers = apiRequestHeaders(forged, secret);
      expect(headers.has(CLIENT_IP_HEADER)).toBe(false);
      expect(headers.has(PROXY_SECRET_HEADER)).toBe(false);
    }
  });

  it("recorta los espacios del secreto configurado", () => {
    const headers = apiRequestHeaders(new Headers({ "x-forwarded-for": "203.0.113.7" }), ` ${SECRET}\n`);

    expect(headers.get(PROXY_SECRET_HEADER)).toBe(SECRET);
  });
});

describe("middleware en /api/v1/*", () => {
  it("con BACKEND_PROXY_SECRET, pide reenviar la IP del navegador al backend", () => {
    vi.stubEnv("BACKEND_PROXY_SECRET", SECRET);

    const response = middleware(apiRequest("/api/v1/auth/login", { "x-forwarded-for": "203.0.113.7:51234" }));

    expect(forwardedRequestHeaders(response)).toEqual({
      clientIp: "203.0.113.7",
      secret: SECRET,
      forwardedFor: "203.0.113.7:51234",
    });
    // La API conserva su propia politica: el middleware no agrega CSP.
    expect(response.headers.get("Content-Security-Policy")).toBeNull();
  });

  it("sin BACKEND_PROXY_SECRET, no reenvia IP ni secreto aunque el navegador los invente", () => {
    vi.stubEnv("BACKEND_PROXY_SECRET", "");

    const response = middleware(apiRequest("/api/v1/signup", {
      "x-forwarded-for": "203.0.113.7:51234",
      [CLIENT_IP_HEADER]: "6.6.6.6",
      [PROXY_SECRET_HEADER]: "adivinado",
    }));

    const forwarded = forwardedRequestHeaders(response);
    expect(forwarded.clientIp).toBeNull();
    expect(forwarded.secret).toBeNull();
  });
});
