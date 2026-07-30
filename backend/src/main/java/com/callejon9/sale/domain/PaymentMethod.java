package com.callejon9.sale.domain;

/**
 * Espejo exacto del CHECK de {@code sales.payment_method} (y de
 * {@code tickets.payment_method}, que usa el mismo catalogo) en
 * V2__data_plane.sql.
 */
public enum PaymentMethod {
    CASH,
    CARD,
    TRANSFER,
    MIXED,
    MERCADOPAGO
}
