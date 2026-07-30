package com.callejon9.kitchen.event;

import com.callejon9.kitchen.web.dto.KitchenItemResponse;
import java.util.UUID;

/**
 * Se publica cuando el {@code kitchenStatus} de un producto de la orden
 * avanza. El payload es un DTO ({@link KitchenItemResponse}), nunca la
 * entidad {@code OrderItem}.
 */
public record KitchenItemStatusChangedEvent(UUID tenantId, KitchenItemResponse item) {
}
