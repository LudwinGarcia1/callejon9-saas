package com.callejon9.analytics.repository;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Proyeccion de la consulta nativa agrupada por dia. Los nombres de los
 * metodos deben calzar (sin distinguir mayusculas) con los alias de columna
 * de {@link AnalyticsRepository#dailySales}.
 */
public interface DailySalesProjection {

    LocalDate getDay();

    Long getCnt();

    BigDecimal getTotal();
}
