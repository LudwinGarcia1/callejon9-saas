package com.callejon9.sale.web.dto;

import java.util.List;

/**
 * Respuesta de {@code GET /api/v1/sales}: las filas del rango solicitado junto
 * con su resumen. Se agrupan en un solo objeto (en vez de una lista suelta mas
 * un resumen separado) porque ambos describen el mismo rango y siempre se
 * consumen juntos en la pantalla de historial -- separarlos solo obligaria al
 * cliente a correlacionar dos respuestas que ya nacen relacionadas.
 */
public record SalesHistoryResponse(List<SaleHistoryRow> sales, SaleHistorySummary summary) {
}
