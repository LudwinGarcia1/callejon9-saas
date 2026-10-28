package com.callejon9.cashier.domain;

import com.callejon9.shared.domain.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "cash_movements")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CashMovement extends TenantScopedEntity {

    @Column(name = "cash_session_id", nullable = false, updatable = false)
    private UUID cashSessionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "movement_type", nullable = false, length = 20, updatable = false)
    private CashMovementType movementType;

    @Column(nullable = false, updatable = false)
    private BigDecimal amount;

    @Column(nullable = false, length = 250, updatable = false)
    private String reason;

    @Column(name = "recorded_by", nullable = false, updatable = false)
    private UUID recordedBy;
}
