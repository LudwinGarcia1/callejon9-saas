package com.callejon9.catalog.web.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateProductStatusRequest(@NotNull Boolean active) {
}
