package com.callejon9.ticket.web.dto;

import com.callejon9.ticket.domain.Ticket;
import com.callejon9.ticket.domain.TicketItemSnapshot;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TicketResponse(
        UUID id,
        UUID saleId,
        UUID orderId,
        String folio,
        List<TicketItemSnapshot> items,
        BigDecimal subtotal,
        BigDecimal tip,
        BigDecimal tipPercent,
        BigDecimal total,
        String paymentMethod,
        Instant closedAt) {

    public static TicketResponse from(Ticket ticket) {
        return new TicketResponse(ticket.getId(), ticket.getSaleId(), ticket.getOrderId(),
                ticket.getFolio(), ticket.getItemsSnapshot(), ticket.getSubtotal(), ticket.getTip(),
                ticket.getTipPercent(), ticket.getTotal(), ticket.getPaymentMethod().name(),
                ticket.getClosedAt());
    }
}
