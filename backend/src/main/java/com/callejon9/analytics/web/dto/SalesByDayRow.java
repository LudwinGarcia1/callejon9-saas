package com.callejon9.analytics.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Ventas de un dia del rango, en la zona horaria del negocio. Un dia sin
 * ventas aparece igual, con {@code total} cero y {@code count} cero, para que
 * la grafica de barras no mienta sobre un hueco en el calendario.
 */
public record SalesByDayRow(LocalDate day, BigDecimal total, long count) {
}
