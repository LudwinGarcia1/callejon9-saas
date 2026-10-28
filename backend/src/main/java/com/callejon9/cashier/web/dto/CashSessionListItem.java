package com.callejon9.cashier.web.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CashSessionListItem(
        UUID id,
        UUID cashierId,
        String cashierName,
        BigDecimal openingFund,
        Instant openedAt,
        Instant closedAt,
        BigDecimal expectedCash,
        BigDecimal countedCash,
        BigDecimal difference,
        String closureReason) {
}
