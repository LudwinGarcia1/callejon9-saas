import { describe, expect, it } from "vitest";
import {
  formatCurrency,
  formatIsoDate,
  formatRoundedCurrency,
  formatShortDate,
  formatShortTime,
} from "../lib/format";

describe("lib/format (Formateadores de moneda, fechas y horas)", () => {
  describe("formatCurrency — Moneda estándar con centavos", () => {
    it("formatea números enteros y decimales con símbolo de pesos y dos decimales", () => {
      const formatted = formatCurrency(499);
      // Incluye $499.00 y no caracteres extraños
      expect(formatted).toContain("499.00");
      expect(formatted).toContain("$");

      const formattedDec = formatCurrency(1250.5);
      expect(formattedDec).toContain("1,250.50");
    });
  });

  describe("formatRoundedCurrency — Moneda redondeada sin centavos", () => {
    it("formatea valores redondeados sin la fracción decimal .00", () => {
      const rounded = formatRoundedCurrency(1000);
      expect(rounded).toContain("1,000");
      expect(rounded).not.toContain(".00");
    });
  });

  describe("formatShortDate y formatShortTime — Fechas y horas en español México", () => {
    it("formatea una fecha ISO sin hora sin desplazar el día por UTC", () => {
      expect(formatIsoDate("2026-10-20")).toBe("20/10/2026");
    });

    it("formatea fechas ISO a fecha corta dd/mm/aaaa", () => {
      // 2026-10-20T12:00:00Z
      const dateFormatted = formatShortDate("2026-10-20T12:00:00Z");
      expect(dateFormatted).toMatch(/20\/10\/2026/);
    });

    it("formatea horas ISO a hora corta hh:mm", () => {
      const timeFormatted = formatShortTime("2026-10-20T14:05:00Z");
      expect(timeFormatted).toMatch(/\d{1,2}:\d{2}/);
    });
  });
});
