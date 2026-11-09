package com.callejon9.payment.web.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import java.math.BigDecimal;

public record CreatePreferenceRequest(
        @DecimalMin(value = "0.0", message = "La propina no puede ser negativa")
        @DecimalMax(value = "100.0", message = "La propina no puede exceder el 100%")
        BigDecimal tipPercent
) {
    public CreatePreferenceRequest {
        if (tipPercent == null) {
            tipPercent = BigDecimal.ZERO;
        }
    }
}
