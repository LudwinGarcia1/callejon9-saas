import { describe, expect, it } from 'vitest';
import { sentryOptions } from '../monitoring/sentryOptions';

describe('sentryOptions', () => {
  it('no inicializa Sentry sin DSN', () => {
    expect(sentryOptions({ dsn: '  ' }, false)).toBeNull();
    expect(sentryOptions({}, true)).toBeNull();
  });

  it('nunca envía PII y deduce el entorno de la build', () => {
    const dsn = 'https://public@o0.ingest.sentry.io/0';
    expect(sentryOptions({ dsn }, false)).toEqual({
      dsn,
      environment: 'production',
      sendDefaultPii: false,
      tracesSampleRate: 0,
    });
    expect(sentryOptions({ dsn }, true)?.environment).toBe('development');
    expect(sentryOptions({ dsn, environment: 'staging' }, false)?.environment).toBe('staging');
  });

  it('acepta solo tasas de trazas entre 0 y 1', () => {
    const dsn = 'https://public@o0.ingest.sentry.io/0';
    expect(sentryOptions({ dsn, tracesSampleRate: '0.2' }, false)?.tracesSampleRate).toBe(0.2);
    expect(sentryOptions({ dsn, tracesSampleRate: '5' }, false)?.tracesSampleRate).toBe(0);
    expect(sentryOptions({ dsn, tracesSampleRate: 'mucho' }, false)?.tracesSampleRate).toBe(0);
  });
});
