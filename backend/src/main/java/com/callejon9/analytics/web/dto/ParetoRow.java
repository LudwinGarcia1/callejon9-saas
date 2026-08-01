package com.callejon9.analytics.web.dto;

import java.math.BigDecimal;

/**
 * Una fila del diagrama de Pareto de productos: cuanto aporto ese producto al
 * ingreso del rango y su lugar en la acumulacion. {@code revenueShare} y
 * {@code cumulativeShare} son porcentajes en escala 0-100, no fracciones
 * 0-1 -- igual convencion en las tres respuestas de analitica.
 */
public record ParetoRow(
        String productName,
        BigDecimal revenue,
        BigDecimal revenueShare,
        BigDecimal cumulativeShare) {
}
