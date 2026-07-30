package com.callejon9.order.domain;

/** Espejo exacto del CHECK de {@code orders.status} en V2__data_plane.sql. */
public enum OrderStatus {
    NEW,
    SENT,
    READY,
    PAID,
    CANCELED
}
