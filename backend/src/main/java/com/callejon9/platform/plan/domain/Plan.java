package com.callejon9.platform.plan.domain;

import com.callejon9.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "plans")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Plan extends BaseEntity {

    @Column(nullable = false, unique = true, length = 40)
    private String code;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "price_monthly", nullable = false)
    private BigDecimal priceMonthly;

    @Column(name = "max_users", nullable = false)
    private int maxUsers;

    @Column(name = "max_tables", nullable = false)
    private int maxTables;
}
