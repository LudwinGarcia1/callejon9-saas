/**
 * Opciones de Sentry compartidas por el navegador, Node y el runtime edge.
 *
 * El DSN se fija al compilar (NEXT_PUBLIC_SENTRY_DSN es un argumento de build
 * de la imagen). Sin DSN el SDK no se inicializa: desarrollo, pruebas y CI no
 * envian nada.
 */

/** Ruta del tunel: el navegador envia sus eventos al mismo origen (CSP `connect-src 'self'`). */
export const SENTRY_TUNNEL_PATH = "/sentry-tunnel";

// Una de cada diez navegaciones y peticiones por defecto; 0 apaga las trazas.
const sampleRate = Number(process.env.NEXT_PUBLIC_SENTRY_TRACES_SAMPLE_RATE ?? "0.1");

const EMAIL = /[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}/g;
const JWT = /eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]*/g;

/** Tacha correos y JWT de un texto antes de que salga hacia Sentry. */
export function redactText(text: string): string {
  return text.replace(JWT, "[jwt]").replace(EMAIL, "[correo]");
}

type SentryLog = { message: unknown; attributes?: Record<string, unknown> };

/** Aplica {@link redactText} al mensaje y a los atributos de texto de un log. */
export function redactLog<T extends SentryLog>(log: T): T {
  if (typeof log.message === "string") log.message = redactText(log.message);
  if (log.attributes) {
    for (const [key, value] of Object.entries(log.attributes)) {
      if (typeof value === "string") log.attributes[key] = redactText(value);
    }
  }
  return log;
}

type SentryErrorEvent = { message?: string; exception?: { values?: { value?: string }[] } };

/** Aplica {@link redactText} al mensaje y a las excepciones de un evento de error. */
export function redactEvent<T extends SentryErrorEvent>(event: T): T {
  if (event.message) event.message = redactText(event.message);
  for (const exception of event.exception?.values ?? []) {
    if (exception.value) exception.value = redactText(exception.value);
  }
  return event;
}

export const sentryOptions = {
  dsn: process.env.NEXT_PUBLIC_SENTRY_DSN || undefined,
  enabled: Boolean(process.env.NEXT_PUBLIC_SENTRY_DSN),
  environment: process.env.NEXT_PUBLIC_SENTRY_ENVIRONMENT || "local",
  release: process.env.NEXT_PUBLIC_SENTRY_RELEASE || undefined,
  // Sin cookies, cabeceras, IP ni cuerpos: el JWT y los datos del restaurante
  // no salen hacia Sentry.
  sendDefaultPii: false,
  tracesSampleRate: Number.isFinite(sampleRate) && sampleRate >= 0 && sampleRate <= 1 ? sampleRate : 0,
  // Los logs de Sentry vienen activos en el SDK; un log o error puede traer un
  // correo dentro del texto, asi que se tacha antes de enviarlo.
  beforeSendLog: redactLog,
  beforeSend: redactEvent,
};

/** Niveles de `console` que se envian a Sentry Logs; `log` e `info` se quedan en local. */
export const SENTRY_CONSOLE_LEVELS = ["warn", "error"] as const;
