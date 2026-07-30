package com.callejon9.catalog.web.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

public record CreateProductRequest(
        @NotBlank @Size(max = 160) String name,
        String description,
        @NotNull @DecimalMin(value = "0.00") BigDecimal price,
        UUID categoryId) {
}
