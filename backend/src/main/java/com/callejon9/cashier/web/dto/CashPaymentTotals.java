package com.callejon9.cashier.web.dto;

import java.math.BigDecimal;

public record CashPaymentTotals(BigDecimal receivedAmount, BigDecimal changeAmount) {
}
