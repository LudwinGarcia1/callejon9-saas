import * as Sentry from "@sentry/nextjs";

import { SENTRY_CONSOLE_LEVELS, SENTRY_TUNNEL_PATH, sentryOptions } from "@/lib/sentry";

Sentry.init({
  ...sentryOptions,
  tunnel: SENTRY_TUNNEL_PATH,
  integrations: [Sentry.consoleLoggingIntegration({ levels: [...SENTRY_CONSOLE_LEVELS] })],
});

export const onRouterTransitionStart = Sentry.captureRouterTransitionStart;
