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

  it("traza una de cada diez por defecto", async () => {
    expect((await loadOptions()).tracesSampleRate).toBe(0.1);
  });

  it("tacha correos y JWT de los logs y de los errores", async () => {
    const { redactLog, redactEvent } = await import("@/lib/sentry");
    const log = redactLog({
      message: "Alta fallida de ana@restaurante.mx",
      attributes: { email: "ana@restaurante.mx", count: 2 },
    });
    expect(log.message).toBe("Alta fallida de [correo]");
    expect(log.attributes).toEqual({ email: "[correo]", count: 2 });

    const event = redactEvent({
      exception: { values: [{ value: "token eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIn0.firma invalido" }] },
    });
    expect(event.exception?.values?.[0].value).toBe("token [jwt] invalido");
  });

  it("deja las trazas en 0 si la tasa no es un numero", async () => {
    vi.stubEnv("NEXT_PUBLIC_SENTRY_TRACES_SAMPLE_RATE", "mucho");
    expect((await loadOptions()).tracesSampleRate).toBe(0);
  });
});
