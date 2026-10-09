package com.callejon9.sale.domain;

import com.callejon9.shared.domain.TenantScopedEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "payments")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Payment extends TenantScopedEntity {
    @Column(name = "sale_id", nullable = false) private UUID saleId;
    @Column(nullable = false) private String provider;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private PaymentMethod method;
    @Column(nullable = false) private BigDecimal amount;
    @Column(name = "received_amount", nullable = false) private BigDecimal receivedAmount;
    @Column(nullable = false) private String status;
}
