package com.callejon9.table.web.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record UpdateTableRequest(
        @NotNull @Min(1) Integer number,
        @NotNull @Min(1) Integer capacity) {
}
