package com.callejon9.kitchen.web.dto;

import com.callejon9.order.service.OrderWithItems;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record KitchenOrderResponse(
        UUID id,
        String folio,
        UUID tableId,
        String status,
        Instant sentToKitchenAt,
        List<KitchenItemResponse> items) {

    public static KitchenOrderResponse from(OrderWithItems orderWithItems) {
        var order = orderWithItems.order();
        var items = orderWithItems.items().stream().map(KitchenItemResponse::from).toList();
        return new KitchenOrderResponse(order.getId(), order.getFolio(), order.getTableId(),
                order.getStatus().name(), order.getSentToKitchenAt(), items);
    }
}
