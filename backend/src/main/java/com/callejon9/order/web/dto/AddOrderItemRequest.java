package com.callejon9.order.web.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record AddOrderItemRequest(
        @NotNull UUID productId,
        @NotNull @Min(1) Integer quantity,
        String notes) {
}
