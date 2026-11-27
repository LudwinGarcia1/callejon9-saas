import * as Sentry from '@sentry/react-native';
import type { ComponentType } from 'react';
import { API_URL } from '../config';
import { sentryOptions } from './sentryOptions';

const options = sentryOptions(
  {
    dsn: process.env.EXPO_PUBLIC_SENTRY_DSN,
    environment: process.env.EXPO_PUBLIC_SENTRY_ENVIRONMENT,
    tracesSampleRate: process.env.EXPO_PUBLIC_SENTRY_TRACES_SAMPLE_RATE,
  },
  __DEV__,
);

/** Inicializa Sentry antes de montar la app. Sin EXPO_PUBLIC_SENTRY_DSN no hace nada. */
export function initSentry(): void {
  if (!options) return;
  Sentry.init({
    ...options,
    // La traza viaja a la API para unir el recorrido de la app con el del backend.
    tracePropagationTargets: API_URL ? [API_URL] : [],
    integrations: [
      Sentry.consoleLoggingIntegration({ levels: ['warn', 'error'] }),
      // Sin React Navigation no hay transacción de navegación a la que colgar el
      // arranque: se envía como transacción propia.
      Sentry.appStartIntegration({ standalone: true }),
    ],
  });
}

/**
 * Abre una transacción por pantalla. Las llamadas a la API que haga la pantalla
 * quedan como spans hijos; la transacción se cierra sola cuando no hay actividad.
 */
export function traceScreen(name: string): void {
  if (options) Sentry.startIdleNavigationSpan({ name, op: 'navigation' });
}

/**
 * Suma 1 a una métrica de Sentry. Solo conteos con atributos de pocos valores:
 * nunca datos del comensal, del pedido ni del restaurante.
 */
export function countMetric(name: string, attributes?: Record<string, string>): void {
  if (options) Sentry.metrics.count(name, 1, attributes ? { attributes } : undefined);
}

type RootComponent = ComponentType<Record<string, unknown>>;

/** Envuelve el componente raíz para capturar errores de render y fallos nativos. */
export function withSentry(App: RootComponent): RootComponent {
  return options ? Sentry.wrap(App) : App;
}
