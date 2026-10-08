package com.callejon9.sale.web.dto;

import com.callejon9.sale.domain.PaymentMethod;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record PaymentRequest(@NotNull PaymentMethod method,
        @NotNull @DecimalMin("0.01") @Digits(integer = 8, fraction = 2) BigDecimal amount) { }
