package com.callejon9.cashier.web.dto;

import com.callejon9.sale.domain.PaymentMethod;
import java.math.BigDecimal;

public record CashPaymentMethodSummary(
        PaymentMethod method,
        long paymentCount,
        BigDecimal appliedAmount,
        BigDecimal receivedAmount,
        BigDecimal changeAmount) {
}
