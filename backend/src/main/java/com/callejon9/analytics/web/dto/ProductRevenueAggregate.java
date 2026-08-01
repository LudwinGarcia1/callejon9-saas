package com.callejon9.analytics.web.dto;

import java.math.BigDecimal;

/**
 * Ingreso agregado de un producto en el rango, ya sumado en SQL. Resultado
 * intermedio del repositorio -- {@link AnalyticsResponse} expone
 * {@link ParetoRow}, que agrega el porcentaje del total y el acumulado.
 */
public record ProductRevenueAggregate(String productName, BigDecimal revenue) {
}
