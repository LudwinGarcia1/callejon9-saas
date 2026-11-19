import type { NextConfig } from "next";
import { COMMON_SECURITY_HEADERS, HSTS_POLICY } from "./src/lib/security-headers";

/**
 * Origen del backend. Es server-only (sin prefijo NEXT_PUBLIC_): este archivo
 * corre en Node, nunca en el navegador.
 *
 * Se lee al compilar, no al arrancar: `next build` resuelve `rewrites()` y
 * guarda el destino en la salida. Cambiar BACKEND_ORIGIN en un servidor ya
 * compilado no mueve el rewrite; hay que recompilar. La imagen de Docker lo
 * recibe como argumento de build (ver Dockerfile).
 */
const BACKEND_ORIGIN = process.env.BACKEND_ORIGIN ?? "http://localhost:8080";

const nextConfig: NextConfig = {
  // Salida autocontenida para la imagen de produccion: server.js con solo
  // las dependencias que usa, sin pnpm ni el node_modules completo. Solo la
  // activa el Dockerfile: en Windows sin permiso para crear enlaces
  // simbolicos, `next build` falla al copiar los enlaces de pnpm a esa salida.
  output: process.env.NEXT_OUTPUT === "standalone" ? "standalone" : undefined,
  poweredByHeader: false,
  async headers() {
    return [{
      source: "/:path*",
      headers: [
        ...COMMON_SECURITY_HEADERS,
        ...(process.env.NODE_ENV === "production" && process.env.SECURITY_HSTS_ENABLED !== "false"
          ? [{ key: "Strict-Transport-Security", value: HSTS_POLICY }]
          : []),
      ],
    }];
  },
  /**
   * El backend no tiene configuracion de CORS y a proposito no se le agrega
   * ninguna. En vez de eso, Next reenvia /api/v1/* al backend del lado del
   * servidor, de modo que el navegador solo habla con localhost:3000: no hay
   * preflight, no hay que administrar `Access-Control-Allow-Credentials` ni
   * una lista de origenes permitidos. Como consecuencia, la cookie de sesion
   * queda inequivocamente first-party, lo cual importa porque se establece
   * con `SameSite=Strict`.
   */
  async rewrites() {
    return [
      {
        source: "/api/v1/:path*",
        destination: `${BACKEND_ORIGIN}/api/v1/:path*`,
      },
      // Canal en tiempo real (CAL-172). Next reenvia tambien el upgrade de
      // WebSocket, asi que la cookie SameSite=Strict llega al handshake igual
      // que a /api/v1/*. El backend valida el Origin contra APP_ALLOWED_ORIGINS.
      {
        source: "/ws",
        destination: `${BACKEND_ORIGIN}/ws`,
      },
    ];
  },
};

export default nextConfig;
