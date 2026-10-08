import { describe, expect, it } from "vitest";

import { validateLogin } from "../lib/login-validation";

const valid = { slug: "la-esquina", email: "admin@esquina.mx", password: "Secreto123!" };

describe("validacion del login antes de enviar", () => {
  it("P01: rechaza campos vacios o solo con espacios", () => {
    const result = validateLogin({ slug: "  ", email: "", password: "" });

    expect(result).toEqual({
      ok: false,
      errors: {
        slug: "Ingresa el identificador del restaurante.",
        email: "Ingresa tu correo.",
        password: "Ingresa tu contraseña.",
      },
    });
  });

  it.each([
    ["slug", { ...valid, slug: "la esquina' OR '1'='1" }],
    ["slug", { ...valid, slug: "ab" }],
    ["email", { ...valid, email: "no-es-correo" }],
    ["email", { ...valid, email: `${"a".repeat(175)}@x.com` }],
    ["password", { ...valid, password: "x".repeat(101) }],
  ] as const)("P02: marca %s invalido con un mensaje controlado", (field, input) => {
    const result = validateLogin(input);

    expect(result.ok).toBe(false);
    if (!result.ok) {
      expect(Object.keys(result.errors)).toEqual([field]);
      // El mensaje nunca repite lo que escribio el usuario.
      expect(result.errors[field]).not.toContain(input[field]);
    }
  });

  it("normaliza el slug y recorta el correo sin tocar la contrasena", () => {
    const result = validateLogin({
      slug: "  La-Esquina ",
      email: " Admin@esquina.mx ",
      password: " con espacios ",
    });

    expect(result).toEqual({
      ok: true,
      data: { slug: "la-esquina", email: "Admin@esquina.mx", password: " con espacios " },
    });
  });
});
