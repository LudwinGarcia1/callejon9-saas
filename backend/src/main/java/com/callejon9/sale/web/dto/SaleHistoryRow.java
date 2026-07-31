package com.callejon9.sale.web.dto;

import com.callejon9.sale.domain.PaymentMethod;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Una fila del historial de ventas: lo que un cajero o dueno quiere ver de un
 * vistazo. {@code tableNumber}, {@code cashierName} y {@code ticketId} pueden
 * ser nulos (mesa eliminada, cajero eliminado, o ticket aun no generado), asi
 * que viajan en sus tipos de referencia, nunca en primitivos.
 */
public record SaleHistoryRow(
        UUID id,
        String orderFolio,
        Integer tableNumber,
        String cashierName,
        UUID ticketId,
        PaymentMethod paymentMethod,
        BigDecimal subtotal,
        BigDecimal tip,
        BigDecimal total,
        Instant createdAt) {
}
