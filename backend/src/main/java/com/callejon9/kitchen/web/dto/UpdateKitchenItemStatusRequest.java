package com.callejon9.kitchen.web.dto;

import com.callejon9.order.domain.KitchenItemStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateKitchenItemStatusRequest(@NotNull KitchenItemStatus status) {
}
