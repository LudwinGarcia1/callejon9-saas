package com.callejon9.order.web.dto;

import com.callejon9.order.domain.Order;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OrderSummaryResponse(
        UUID id,
        String folio,
        UUID tableId,
        UUID waiterId,
        int guestCount,
        String status,
        BigDecimal total,
        Instant openedAt) {

    public static OrderSummaryResponse from(Order order) {
        return new OrderSummaryResponse(order.getId(), order.getFolio(), order.getTableId(),
                order.getWaiterId(), order.getGuestCount(), order.getStatus().name(),
                order.getTotal(), order.getOpenedAt());
    }
}
