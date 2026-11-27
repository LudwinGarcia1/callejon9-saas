/**
 * Opciones de Sentry compartidas por el navegador, Node y el runtime edge.
 *
 * El DSN se fija al compilar (NEXT_PUBLIC_SENTRY_DSN es un argumento de build
 * de la imagen). Sin DSN el SDK no se inicializa: desarrollo, pruebas y CI no
 * envian nada.
 */

/** Ruta del tunel: el navegador envia sus eventos al mismo origen (CSP `connect-src 'self'`). */
export const SENTRY_TUNNEL_PATH = "/sentry-tunnel";

const sampleRate = Number(process.env.NEXT_PUBLIC_SENTRY_TRACES_SAMPLE_RATE ?? "0");

export const sentryOptions = {
  dsn: process.env.NEXT_PUBLIC_SENTRY_DSN || undefined,
  enabled: Boolean(process.env.NEXT_PUBLIC_SENTRY_DSN),
  environment: process.env.NEXT_PUBLIC_SENTRY_ENVIRONMENT || "local",
  release: process.env.NEXT_PUBLIC_SENTRY_RELEASE || undefined,
  // Sin cookies, cabeceras, IP ni cuerpos: el JWT y los datos del restaurante
  // no salen hacia Sentry.
  sendDefaultPii: false,
  tracesSampleRate: Number.isFinite(sampleRate) ? sampleRate : 0,
};
