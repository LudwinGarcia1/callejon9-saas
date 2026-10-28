package com.callejon9.cashier.web.dto;

import com.callejon9.cashier.domain.CashMovementType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CashMovementResponse(
        UUID id, CashMovementType type, BigDecimal amount, String reason, UUID recordedBy, Instant createdAt) {
}
