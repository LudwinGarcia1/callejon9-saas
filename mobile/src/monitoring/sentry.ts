import * as Sentry from '@sentry/react-native';
import type { ComponentType } from 'react';
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
  if (options) Sentry.init(options);
}

type RootComponent = ComponentType<Record<string, unknown>>;

/** Envuelve el componente raíz para capturar errores de render y fallos nativos. */
export function withSentry(App: RootComponent): RootComponent {
  return options ? Sentry.wrap(App) : App;
}
