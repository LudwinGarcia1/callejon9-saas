package com.callejon9.sale.web.dto;

import com.callejon9.sale.domain.PaymentMethod;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record CheckoutRequest(
        @NotNull PaymentMethod paymentMethod,
        @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal tipPercent) {
}
