package com.callejon9.kitchen.web.dto;

import com.callejon9.order.domain.OrderItem;
import java.util.UUID;

public record KitchenItemResponse(
        UUID id,
        UUID orderId,
        UUID productId,
        String productName,
        int quantity,
        String kitchenStatus,
        String notes) {

    public static KitchenItemResponse from(OrderItem item) {
        return new KitchenItemResponse(item.getId(), item.getOrderId(), item.getProductId(),
                item.getProductName(), item.getQuantity(), item.getKitchenStatus().name(),
                item.getNotes());
    }
}
