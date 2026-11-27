import * as Sentry from "@sentry/nextjs";

import { SENTRY_TUNNEL_PATH, sentryOptions } from "@/lib/sentry";

Sentry.init({
  ...sentryOptions,
  tunnel: SENTRY_TUNNEL_PATH,
});

export const onRouterTransitionStart = Sentry.captureRouterTransitionStart;
