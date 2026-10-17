package com.callejon9.order.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record OpenOrderRequest(
        @NotNull UUID tableId,
        @NotNull @Min(1) @Max(50) Integer guestCount) {
}
