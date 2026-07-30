import type { NextConfig } from "next";

/**
 * Origen del backend. Es server-only (sin prefijo NEXT_PUBLIC_) porque este
 * archivo corre en Node al iniciar el servidor de Next, nunca en el
 * navegador.
 */
const BACKEND_ORIGIN = process.env.BACKEND_ORIGIN ?? "http://localhost:8080";

const nextConfig: NextConfig = {
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
    ];
  },
};

export default nextConfig;
