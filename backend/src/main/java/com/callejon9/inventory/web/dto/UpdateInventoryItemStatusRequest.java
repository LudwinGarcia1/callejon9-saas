package com.callejon9.inventory.web.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateInventoryItemStatusRequest(@NotNull Boolean active) {
}
