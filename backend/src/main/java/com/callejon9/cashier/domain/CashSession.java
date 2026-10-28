package com.callejon9.cashier.domain;

import com.callejon9.shared.domain.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "cash_sessions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CashSession extends TenantScopedEntity {

    @Column(name = "cashier_id", nullable = false, updatable = false)
    private UUID cashierId;

    @Column(name = "opening_fund", nullable = false, updatable = false)
    private BigDecimal openingFund;

    @Column(name = "opened_at", nullable = false, updatable = false)
    private Instant openedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "closed_by")
    private UUID closedBy;

    @Column(name = "expected_cash")
    private BigDecimal expectedCash;

    @Column(name = "counted_cash")
    private BigDecimal countedCash;

    @Column(name = "difference")
    private BigDecimal difference;

    @Column(name = "closure_reason", length = 250)
    private String closureReason;

    public boolean isOpen() {
        return closedAt == null;
    }
}
