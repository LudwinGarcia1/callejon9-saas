import { afterEach, describe, expect, it, vi } from "vitest";

async function loadOptions() {
  vi.resetModules();
  return (await import("@/lib/sentry")).sentryOptions;
}

describe("opciones de Sentry", () => {
  afterEach(() => {
    vi.unstubAllEnvs();
  });

  it("queda apagado sin NEXT_PUBLIC_SENTRY_DSN", async () => {
    vi.stubEnv("NEXT_PUBLIC_SENTRY_DSN", "");
    const options = await loadOptions();

    expect(options.enabled).toBe(false);
    expect(options.dsn).toBeUndefined();
  });

  it("se activa con DSN y nunca envia PII", async () => {
    vi.stubEnv("NEXT_PUBLIC_SENTRY_DSN", "https://public@o0.ingest.sentry.io/0");
    const options = await loadOptions();

    expect(options.enabled).toBe(true);
    expect(options.sendDefaultPii).toBe(false);
  });

  it("deja las trazas en 0 si la tasa no es un numero", async () => {
    vi.stubEnv("NEXT_PUBLIC_SENTRY_TRACES_SAMPLE_RATE", "mucho");
    expect((await loadOptions()).tracesSampleRate).toBe(0);
  });
});
