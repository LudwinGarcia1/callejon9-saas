/**
 * Tunel de Sentry: el navegador envia sus eventos a este mismo origen y el
 * servidor los reenvia a Sentry sin las cabeceras de la peticion (cookies
 * incluidas). Solo acepta el DSN configurado; cualquier otro recibe 403.
 */
export { POST } from "@sentry/nextjs/tunnel";
