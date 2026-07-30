package com.callejon9.ticket.domain;

import com.callejon9.sale.domain.PaymentMethod;
import com.callejon9.shared.domain.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * El ticket es un documento inmutable: {@code itemsSnapshot} se guarda como
 * jsonb deliberadamente, de modo que un cambio posterior en el precio de un
 * producto nunca altere un ticket ya emitido.
 */
@Entity
@Table(name = "tickets")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Ticket extends TenantScopedEntity {

    @Column(name = "sale_id", nullable = false)
    private UUID saleId;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(nullable = false, length = 40)
    private String folio;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "items_snapshot", nullable = false, columnDefinition = "jsonb")
    private List<TicketItemSnapshot> itemsSnapshot;

    @Column(nullable = false)
    private BigDecimal subtotal;

    @Column(nullable = false)
    private BigDecimal tip;

    @Column(name = "tip_percent", nullable = false)
    private BigDecimal tipPercent;

    @Column(nullable = false)
    private BigDecimal total;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 20)
    private PaymentMethod paymentMethod;

    @Column(name = "closed_at", nullable = false)
    private Instant closedAt;
}
