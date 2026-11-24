package com.callejon9.sale.web.dto;

import com.callejon9.sale.domain.PaymentMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

public record CheckoutRequest(
        PaymentMethod paymentMethod,
        @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal tipPercent,
        @Size(min = 1, max = 50) List<@NotNull @Valid PaymentRequest> payments) {}
