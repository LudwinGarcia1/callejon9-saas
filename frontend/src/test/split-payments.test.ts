import { expect, it } from "vitest";
import { paymentBalance, splitEqually } from "@/lib/split-payments";

it("distributes the remainder in cents", () => {
  expect(splitEqually(100, 3).map((p) => p.amount)).toEqual([33.34, 33.33, 33.33]);
  expect(splitEqually(0.02, 3)).toEqual([]);
  expect(splitEqually(100, 0)).toEqual([]);
  expect(splitEqually(100, 1.5)).toEqual([]);
  expect(splitEqually(100, 51)).toEqual([]);
});
it("permits mixed payment and only cash excess", () => {
  expect(paymentBalance([{ method: "CASH", amount: 300 }, { method: "CARD", amount: 200 }], 500).valid).toBe(true);
  expect(paymentBalance([{ method: "CASH", amount: 600 }], 500)).toMatchObject({ valid: true, change: 100 });
  expect(paymentBalance([{ method: "CARD", amount: 600 }], 500)).toMatchObject({ valid: false, nonCashExcess: true });
  expect(paymentBalance([{ method: "CASH", amount: 300 }], 500)).toMatchObject({ valid: false, remaining: 200 });
});
it("rejects invalid precision and nonfinite input", () => {
  for (const amount of [0, -1, 1.001, NaN, Infinity]) expect(paymentBalance([{ method: "CASH", amount }], 1).valid).toBe(false);
});
