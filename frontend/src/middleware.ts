import { NextResponse } from "next/server";
import type { NextRequest } from "next/server";

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
export const PATHNAME_HEADER = "x-pathname";

export function middleware(request: NextRequest) {
  const hasAccessToken = request.cookies.has("access_token");

  if (!hasAccessToken) {
    const loginUrl = new URL("/login", request.url);
    loginUrl.searchParams.set("next", request.nextUrl.pathname);
    return NextResponse.redirect(loginUrl);
  }

  const headers = new Headers(request.headers);
  headers.set(PATHNAME_HEADER, request.nextUrl.pathname);

  return NextResponse.next({ request: { headers } });
}

/**
 * Solo rutas protegidas por sesion. `/api` queda deliberadamente fuera: si
 * el rewrite de next.config.ts hacia el backend pasara por este middleware,
 * quedaria atrapado detras del mismo gate que protege a las paginas.
 */
export const config = {
  matcher: [
    "/admin/:path*",
    "/cashier/:path*",
    "/kitchen/:path*",
    "/platform/:path*",
    "/waiter/:path*",
  ],
};
