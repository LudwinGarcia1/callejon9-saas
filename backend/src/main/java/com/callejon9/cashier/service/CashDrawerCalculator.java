package com.callejon9.cashier.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Cálculo puro del efectivo esperado para que checkout y arqueo usen la misma regla. */
public final class CashDrawerCalculator {

    private CashDrawerCalculator() {
    }

    public static BigDecimal expectedCash(
            BigDecimal openingFund,
            BigDecimal cashReceived,
            BigDecimal changeGiven,
            BigDecimal deposits,
            BigDecimal withdrawals) {
        return openingFund.add(cashReceived).subtract(changeGiven)
                .add(deposits).subtract(withdrawals).setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal difference(BigDecimal expectedCash, BigDecimal countedCash) {
        return countedCash.subtract(expectedCash).setScale(2, RoundingMode.HALF_UP);
    }
}
