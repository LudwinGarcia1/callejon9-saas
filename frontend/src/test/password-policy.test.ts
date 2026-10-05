import { describe, expect, it } from "vitest";

import { passwordRules, validateNewPassword } from "../lib/password-policy";

const account = ["La Esquina", "la-esquina", "jperez@correo.mx", "Juan Pérez"];

describe("politica de contrasenas en el cliente", () => {
  it("acepta contrasenas razonables", () => {
    expect(validateNewPassword("Mantel-Azul-47", account)).toBeNull();
    expect(validateNewPassword("Lámpara8Río3")).toBeNull();
  });

  it("pide una contrasena cuando esta vacia", () => {
    expect(validateNewPassword("")).toBe("Ingresa una contraseña.");
  });

  it.each([
    ["Ab1cdefgh", "al menos 10 caracteres"],
    ["a1" + "ñ".repeat(36), "no puede exceder 72"],
    ["solamenteletras", "combinar letras y números"],
    ["aaaaaaaaa1", "repite demasiado"],
    ["Perez-2031x", "no debe incluir tu nombre"],
    ["jperez7781!", "no debe incluir tu nombre"],
    ["ESQUINA-norte-9", "no debe incluir tu nombre"],
  ])("rechaza %s con el mismo mensaje que el backend", (password, fragment) => {
    expect(validateNewPassword(password, account)).toContain(fragment);
  });

  it("el mensaje nunca repite la contrasena", () => {
    expect(validateNewPassword("Perez-2031x", account)).not.toContain("Perez-2031x");
  });

  it("marca cada requisito mientras se escribe", () => {
    const met = (password: string) =>
      Object.fromEntries(passwordRules(password, account).map((rule) => [rule.id, rule.met]));

    expect(met("")).toEqual({
      length: false,
      letterAndDigit: false,
      variety: false,
      personalData: false,
    });
    expect(met("Mantel-Azul-47")).toEqual({
      length: true,
      letterAndDigit: true,
      variety: true,
      personalData: true,
    });
    expect(met("juanito12345").personalData).toBe(false);
  });
});
