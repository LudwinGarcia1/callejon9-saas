/** Variables de entorno de Sentry, ya leídas (Expo solo inlinea accesos estáticos). */
export interface SentryEnv {
  dsn?: string;
  environment?: string;
  tracesSampleRate?: string;
}

export interface SentryOptions {
  dsn: string;
  environment: string;
  sendDefaultPii: false;
  tracesSampleRate: number;
}

/**
 * Opciones de Sentry para la app, o null si no hay DSN: sin DSN el SDK no se
 * inicializa y la app no envía nada. Nunca envía PII (IP, cookies,
 * cabeceras): la sesión y los datos del restaurante no salen hacia Sentry.
 */
export function sentryOptions(env: SentryEnv, dev: boolean): SentryOptions | null {
  const dsn = (env.dsn ?? '').trim();
  if (!dsn) return null;
  const rate = Number(env.tracesSampleRate ?? '0');
  return {
    dsn,
    environment: env.environment?.trim() || (dev ? 'development' : 'production'),
    sendDefaultPii: false,
    tracesSampleRate: Number.isFinite(rate) && rate >= 0 && rate <= 1 ? rate : 0,
  };
}
