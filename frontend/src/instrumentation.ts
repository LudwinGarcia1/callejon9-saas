import * as Sentry from "@sentry/nextjs";

import { SENTRY_CONSOLE_LEVELS, sentryOptions } from "@/lib/sentry";

/** Inicializa Sentry en el servidor de Next (Node) y en el runtime edge del middleware. */
export function register() {
  if (process.env.NEXT_RUNTIME === "nodejs" || process.env.NEXT_RUNTIME === "edge") {
    Sentry.init({
      ...sentryOptions,
      integrations: [Sentry.consoleLoggingIntegration({ levels: [...SENTRY_CONSOLE_LEVELS] })],
    });
  }
}

/** Reporta los errores de Server Components, Route Handlers y middleware. */
export const onRequestError = Sentry.captureRequestError;
