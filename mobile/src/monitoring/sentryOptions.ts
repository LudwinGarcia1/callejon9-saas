/** Variables de entorno de Sentry, ya leídas (Expo solo inlinea accesos estáticos). */
export interface SentryEnv {
  dsn?: string;
  environment?: string;
  tracesSampleRate?: string;
}

type SentryLog = { message: unknown; attributes?: Record<string, unknown> };

export interface SentryOptions {
  dsn: string;
  environment: string;
  sendDefaultPii: false;
  tracesSampleRate: number;
  enableLogs: true;
  logsOrigin: 'js';
  enableAutoConsoleLogs: false;
  beforeSendLog: <T extends SentryLog>(log: T) => T;
}

const EMAIL = /[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}/g;
const JWT = /eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]*/g;

/** Tacha correos y JWT de un texto antes de que salga hacia Sentry. */
export function redactText(text: string): string {
  return text.replace(JWT, '[jwt]').replace(EMAIL, '[correo]');
}

/** Aplica {@link redactText} al mensaje y a los atributos de texto de un log. */
export function redactLog<T extends SentryLog>(log: T): T {
  if (typeof log.message === 'string') log.message = redactText(log.message);
  if (log.attributes) {
    for (const [key, value] of Object.entries(log.attributes)) {
      if (typeof value === 'string') log.attributes[key] = redactText(value);
    }
  }
  return log;
}

/**
 * Opciones de Sentry para la app, o null si no hay DSN: sin DSN el SDK no se
 * inicializa y la app no envía nada. Nunca envía PII (IP, cookies,
 * cabeceras): la sesión y los datos del restaurante no salen hacia Sentry.
 *
 * Traza una de cada diez sesiones por defecto y envía a Sentry Logs los
 * `console.warn` y `console.error` de JavaScript (la integración se agrega en
 * sentry.ts), con correos y JWT tachados.
 */
export function sentryOptions(env: SentryEnv, dev: boolean): SentryOptions | null {
  const dsn = (env.dsn ?? '').trim();
  if (!dsn) return null;
  const rate = Number(env.tracesSampleRate ?? '0.1');
  return {
    dsn,
    environment: env.environment?.trim() || (dev ? 'development' : 'production'),
    sendDefaultPii: false,
    tracesSampleRate: Number.isFinite(rate) && rate >= 0 && rate <= 1 ? rate : 0,
    enableLogs: true,
    logsOrigin: 'js',
    enableAutoConsoleLogs: false,
    beforeSendLog: redactLog,
  };
}
