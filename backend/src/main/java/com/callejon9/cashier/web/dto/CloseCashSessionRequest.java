package com.callejon9.cashier.web.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record CloseCashSessionRequest(
        @NotNull @DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal countedCash,
        @Size(max = 250) String reason) {
}
