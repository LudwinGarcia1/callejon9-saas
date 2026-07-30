package com.callejon9.order.web.dto;

import com.callejon9.order.domain.OrderItem;
import java.math.BigDecimal;
import java.util.UUID;

public record OrderItemResponse(
        UUID id,
        UUID productId,
        String productName,
        BigDecimal unitPrice,
        int quantity,
        String kitchenStatus,
        String notes) {

    public static OrderItemResponse from(OrderItem item) {
        return new OrderItemResponse(item.getId(), item.getProductId(), item.getProductName(),
                item.getUnitPrice(), item.getQuantity(), item.getKitchenStatus().name(),
                item.getNotes());
    }
}
