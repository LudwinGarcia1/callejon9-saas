import { describe, expect, it } from "vitest";

import { postLoginPath } from "../lib/auth-redirect";

describe("destino posterior al login", () => {
  const fallback = "/admin";

  it.each(["/waiter/order/123", "/analytics?range=week", "/history#today"])(
    "acepta la ruta interna %s",
    (nextPath) => {
      expect(postLoginPath(nextPath, fallback)).toBe(nextPath);
    },
  );

  it.each([
    null,
    "",
    " https://externo.example",
    "https://externo.example",
    "//externo.example",
    "\\\\externo.example",
    "/%2Fexterno.example",
    "/%5Cexterno.example",
    "%2Fwaiter",
    "/%E0%A4%A",
  ])("usa el fallback para %s", (nextPath) => {
    expect(postLoginPath(nextPath, fallback)).toBe(fallback);
  });
});
