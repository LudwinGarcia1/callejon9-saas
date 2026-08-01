package com.callejon9.analytics.web.dto;

import java.util.List;

/**
 * Respuesta de {@code GET /api/v1/analytics}: los tres conjuntos de datos que
 * reconectan la pantalla de analitica con la metodologia CRISP-DM original
 * (Pareto de productos, distribucion de ventas por dia, mezcla de pago) para
 * el mismo rango solicitado.
 */
public record AnalyticsResponse(
        List<ParetoRow> pareto,
        List<SalesByDayRow> salesByDay,
        List<PaymentMixRow> paymentMix) {
}
