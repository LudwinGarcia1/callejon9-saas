package com.callejon9.analytics.web.dto;

import java.math.BigDecimal;

/**
 * Un producto del ranking de mas vendidos del rango: unidades vendidas e
 * ingreso que generaron, ya sumados en SQL. A diferencia de {@link ParetoRow},
 * el orden es por unidades, no por ingreso.
 */
public record TopProductRow(String productName, long quantity, BigDecimal revenue) {
}
