import { redirect } from "next/navigation";

/**
 * Raiz de la aplicacion. No tiene contenido propio: la puerta de entrada es
 * `/login`, que ya sabe a que pantalla mandar a cada rol una vez autenticado.
 *
 * La decision se toma en el servidor y no consultando `GET /auth/me`: sin
 * sesion ese endpoint responde 401, y el manejador global de `providers.tsx`
 * lo interpretaria como sesion expirada y mostraria ese aviso a alguien que
 * nunca llego a iniciar sesion.
 */
export default function Home() {
  redirect("/login");
}
