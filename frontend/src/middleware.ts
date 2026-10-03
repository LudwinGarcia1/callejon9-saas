import { NextResponse } from "next/server";
import type { NextRequest } from "next/server";

import { isProtectedPagePath, loginPathFor } from "@/lib/protected-pages";
import { PATHNAME_HEADER } from "@/lib/request-headers";
import { contentSecurityPolicy } from "@/lib/security-headers";

/**
 * Verificacion de solo presencia: si falta la cookie `access_token` en una
 * ruta protegida, redirige a /login. No se valida la firma del JWT aqui a
 * proposito — hacerlo obligaria a duplicar el secreto HS256 del backend en
 * el frontend, algo arquitectonicamente indefendible. La validacion real de
 * la firma y la expiracion ocurre en cada peticion al backend a traves de
 * su propio filtro de seguridad.
 */
/**
 * Next no le pasa la ruta a un layout de servidor, y el layout raiz necesita
 * saberla para decidir el tema antes del primer pintado. Publicarla como
 * cabecera de peticion es la via soportada.
 */
export function middleware(request: NextRequest) {
  // La API conserva el rewrite y la politica emitida por Spring.
  if (request.nextUrl.pathname === "/api" || request.nextUrl.pathname.startsWith("/api/")) {
    return NextResponse.next();
  }

  const nonceBytes = crypto.getRandomValues(new Uint8Array(16));
  const nonce = btoa(String.fromCharCode(...nonceBytes));
  const csp = contentSecurityPolicy(nonce, process.env.NODE_ENV === "development");
  const hasAccessToken = request.cookies.has("access_token");

  if (isProtectedPagePath(request.nextUrl.pathname) && !hasAccessToken) {
    const loginUrl = new URL(loginPathFor(request.nextUrl.pathname), request.url);
    const response = NextResponse.redirect(loginUrl);
    response.headers.set("Content-Security-Policy", csp);
    return response;
  }

  const headers = new Headers(request.headers);
  headers.set(PATHNAME_HEADER, request.nextUrl.pathname);
  // Next extrae este nonce y lo aplica a sus scripts durante el renderizado.
  headers.set("Content-Security-Policy", csp);
  headers.set("x-nonce", nonce);

  const response = NextResponse.next({ request: { headers } });
  response.headers.set("Content-Security-Policy", csp);
  return response;
}

/**
 * Incluye login, alta, paginas y errores. Los assets no necesitan nonce.
 * La API pasa sin el gate de sesion para conservar el rewrite al backend.
 */
export const config = {
  matcher: ["/((?!_next/static|_next/image|favicon.ico).*)"],
};
