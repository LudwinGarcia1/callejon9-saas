package com.callejon9.table.domain;

/** Espejo exacto del CHECK de {@code restaurant_tables.status} en V2__data_plane.sql. */
public enum TableStatus {
    FREE,
    OCCUPIED,
    RESERVED,
    CLEANING
}
