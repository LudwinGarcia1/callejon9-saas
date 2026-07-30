package com.callejon9.order.domain;

/** Espejo exacto del CHECK de {@code order_items.kitchen_status} en V2__data_plane.sql. */
public enum KitchenItemStatus {
    PENDING,
    IN_PREPARATION,
    READY,
    DELIVERED
}
