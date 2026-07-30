package com.callejon9.order.web.dto;

import com.callejon9.order.service.OrderWithItems;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderResponse(
        UUID id,
        String folio,
        UUID tableId,
        UUID waiterId,
        int guestCount,
        String status,
        BigDecimal total,
        Instant openedAt,
        Instant sentToKitchenAt,
        Instant closedAt,
        List<OrderItemResponse> items) {

    public static OrderResponse from(OrderWithItems orderWithItems) {
        var order = orderWithItems.order();
        var items = orderWithItems.items().stream().map(OrderItemResponse::from).toList();
        return new OrderResponse(order.getId(), order.getFolio(), order.getTableId(),
                order.getWaiterId(), order.getGuestCount(), order.getStatus().name(),
                order.getTotal(), order.getOpenedAt(), order.getSentToKitchenAt(),
                order.getClosedAt(), items);
    }
}
