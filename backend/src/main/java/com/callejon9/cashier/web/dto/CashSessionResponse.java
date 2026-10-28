package com.callejon9.cashier.web.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CashSessionResponse(
        UUID id,
        UUID cashierId,
        String cashierName,
        BigDecimal openingFund,
        Instant openedAt,
        Instant closedAt,
        UUID closedBy,
        BigDecimal expectedCash,
        BigDecimal countedCash,
        BigDecimal difference,
        String closureReason,
        List<CashPaymentMethodSummary> paymentSummary,
        List<CashMovementResponse> movements) {
}
