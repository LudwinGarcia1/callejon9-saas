package com.callejon9.order.domain;

import com.callejon9.shared.domain.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "orders")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Order extends TenantScopedEntity {

    @Column(nullable = false, length = 40)
    private String folio;

    @Column(name = "table_id")
    private UUID tableId;

    @Column(name = "waiter_id")
    private UUID waiterId;

    @Column(name = "guest_count", nullable = false)
    private int guestCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    @Column(nullable = false)
    private BigDecimal total;

    @Column(name = "opened_at", nullable = false)
    private Instant openedAt;

    @Column(name = "sent_to_kitchen_at")
    private Instant sentToKitchenAt;

    @Column(name = "closed_at")
    private Instant closedAt;
}
