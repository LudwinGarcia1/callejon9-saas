import { describe, expect, it } from "vitest";
import { elapsedLabel, orderAge } from "../lib/kitchen-timing";

describe("lib/kitchen-timing (Semáforo y temporizadores de cocina)", () => {
  const BASE_TIME = 1_700_000_000_000;

  describe("orderAge — Clasificación del semáforo", () => {
    it("devuelve 'normal' si la fecha de envío es nula", () => {
      expect(orderAge(null, BASE_TIME)).toBe("normal");
    });

    it("devuelve 'normal' si han transcurrido menos de 15 minutos", () => {
      const tenMinutesAgo = new Date(BASE_TIME - 10 * 60_000).toISOString();
      expect(orderAge(tenMinutesAgo, BASE_TIME)).toBe("normal");
    });

    it("devuelve 'warning' a partir de los 15 minutos", () => {
      const fifteenMinutesAgo = new Date(BASE_TIME - 15 * 60_000).toISOString();
      expect(orderAge(fifteenMinutesAgo, BASE_TIME)).toBe("warning");

      const twentyMinutesAgo = new Date(BASE_TIME - 20 * 60_000).toISOString();
      expect(orderAge(twentyMinutesAgo, BASE_TIME)).toBe("warning");
    });

    it("devuelve 'critical' a partir de los 25 minutos de espera", () => {
      const twentyFiveMinutesAgo = new Date(BASE_TIME - 25 * 60_000).toISOString();
      expect(orderAge(twentyFiveMinutesAgo, BASE_TIME)).toBe("critical");

      const fortyMinutesAgo = new Date(BASE_TIME - 40 * 60_000).toISOString();
      expect(orderAge(fortyMinutesAgo, BASE_TIME)).toBe("critical");
    });

    it("borde: un segundo antes de cada umbral conserva el color anterior", () => {
      const justBeforeWarning = new Date(BASE_TIME - (15 * 60_000 - 1_000)).toISOString();
      expect(orderAge(justBeforeWarning, BASE_TIME)).toBe("normal");

      const justBeforeCritical = new Date(BASE_TIME - (25 * 60_000 - 1_000)).toISOString();
      expect(orderAge(justBeforeCritical, BASE_TIME)).toBe("warning");
    });

    it("borde: una fecha de envío en el futuro (reloj adelantado) se trata como 'normal'", () => {
      const inTwoMinutes = new Date(BASE_TIME + 2 * 60_000).toISOString();
      expect(orderAge(inTwoMinutes, BASE_TIME)).toBe("normal");
    });
  });

  describe("elapsedLabel — Etiquetas de tiempo transcurrido en lenguaje humano", () => {
    it("devuelve string vacío si sentAt es nulo", () => {
      expect(elapsedLabel(null, BASE_TIME)).toBe("");
    });

    it("indica 'recién enviada' cuando el tiempo transcurrido es menor a 1 minuto", () => {
      const thirtySecondsAgo = new Date(BASE_TIME - 30_000).toISOString();
      expect(elapsedLabel(thirtySecondsAgo, BASE_TIME)).toBe("recién enviada");
    });

    it("indica 'hace X min' para tiempos menores a 60 minutos", () => {
      const fiveMinutesAgo = new Date(BASE_TIME - 5 * 60_000).toISOString();
      expect(elapsedLabel(fiveMinutesAgo, BASE_TIME)).toBe("hace 5 min");

      const fiftyNineMinutesAgo = new Date(BASE_TIME - 59 * 60_000).toISOString();
      expect(elapsedLabel(fiftyNineMinutesAgo, BASE_TIME)).toBe("hace 59 min");
    });

    it("indica 'hace X h' o 'hace X h Y min' cuando supera 1 hora", () => {
      const sixtyMinutesAgo = new Date(BASE_TIME - 60 * 60_000).toISOString();
      expect(elapsedLabel(sixtyMinutesAgo, BASE_TIME)).toBe("hace 1 h");

      const oneHourTwentyMinutesAgo = new Date(BASE_TIME - 80 * 60_000).toISOString();
      expect(elapsedLabel(oneHourTwentyMinutesAgo, BASE_TIME)).toBe("hace 1 h 20 min");
    });

    it("borde: 59 s sigue 'recién enviada' y 60 s ya es 'hace 1 min'", () => {
      const fiftyNineSecondsAgo = new Date(BASE_TIME - 59_000).toISOString();
      expect(elapsedLabel(fiftyNineSecondsAgo, BASE_TIME)).toBe("recién enviada");

      const sixtySecondsAgo = new Date(BASE_TIME - 60_000).toISOString();
      expect(elapsedLabel(sixtySecondsAgo, BASE_TIME)).toBe("hace 1 min");
    });

    it("borde: 119 min es 'hace 1 h 59 min' y 120 min es 'hace 2 h'", () => {
      const oneHourFiftyNineAgo = new Date(BASE_TIME - 119 * 60_000).toISOString();
      expect(elapsedLabel(oneHourFiftyNineAgo, BASE_TIME)).toBe("hace 1 h 59 min");

      const twoHoursAgo = new Date(BASE_TIME - 120 * 60_000).toISOString();
      expect(elapsedLabel(twoHoursAgo, BASE_TIME)).toBe("hace 2 h");
    });

    it("borde: una fecha de envío en el futuro no muestra minutos negativos", () => {
      const inTwoMinutes = new Date(BASE_TIME + 2 * 60_000).toISOString();
      expect(elapsedLabel(inTwoMinutes, BASE_TIME)).toBe("recién enviada");
    });
  });
});
