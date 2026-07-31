package com.callejon9.sale.web.dto;

import java.math.BigDecimal;

/** Numero de ventas y suma de sus totales para el rango solicitado, calculado en la consulta. */
public record SaleHistorySummary(long count, BigDecimal total) {
}
