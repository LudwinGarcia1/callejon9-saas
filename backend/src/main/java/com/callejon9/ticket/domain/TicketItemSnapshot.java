package com.callejon9.ticket.domain;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Fotografia inmutable de una linea de la orden al momento del cobro.
 *
 * Se serializa tal cual dentro de {@code tickets.items_snapshot} (jsonb): si
 * el precio del producto cambia despues, el ticket ya emitido no debe
 * cambiar.
 */
public record TicketItemSnapshot(
        UUID productId,
        String productName,
        BigDecimal unitPrice,
        int quantity,
        BigDecimal subtotal) {
}
