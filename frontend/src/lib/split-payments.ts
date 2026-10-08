import type { PaymentRequest } from "./types";

/** Centavos enteros; el residuo se distribuye sin perder centavos. */
export function splitEqually(total: number, count: number): PaymentRequest[] {
  const cents = Math.round(total * 100);
  if (!Number.isSafeInteger(cents) || cents <= 0 || !Number.isInteger(count) || count < 1 || count > 50 || count > cents) return [];
  const base = Math.floor(cents / count);
  return Array.from({ length: count }, (_, index) => ({ method: "CASH", amount: (base + (index < cents % count ? 1 : 0)) / 100 }));
}

export function paymentBalance(payments: PaymentRequest[], total: number) {
  const totalCents = Math.round(total * 100);
  const valid = payments.length > 0 && payments.length <= 50 && Number.isSafeInteger(totalCents) && totalCents > 0
    && payments.every((p) => Number.isFinite(p.amount) && p.amount > 0 && p.amount <= 99999999.99
      && Math.abs(p.amount * 100 - Math.round(p.amount * 100)) < 0.000001);
  const received = payments.reduce((sum, p) => sum + Math.round(p.amount * 100), 0);
  const nonCash = payments.filter((p) => p.method !== "CASH").reduce((sum, p) => sum + Math.round(p.amount * 100), 0);
  return { valid: valid && received >= totalCents && nonCash <= totalCents,
    remaining: Math.max(0, totalCents - received) / 100,
    change: Math.max(0, received - totalCents) / 100,
    nonCashExcess: nonCash > totalCents };
}
