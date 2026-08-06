package com.callejon9.inventory.domain;

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

/**
 * Una fila del ledger. La suma de las cantidades de todos los movimientos de
 * un insumo cuadra siempre con su columna stock: no existe ningun camino que
 * cambie el stock sin dejar una fila aqui.
 */
@Entity
@Table(name = "inventory_movements")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryMovement extends TenantScopedEntity {

    @Column(name = "inventory_item_id", nullable = false)
    private UUID inventoryItemId;

    @Enumerated(EnumType.STRING)
    @Column(name = "movement_type", nullable = false, length = 20)
    private InventoryMovementType movementType;

    /** Con signo unicamente en ADJUSTMENT; en los otros tres siempre positiva. */
    @Column(nullable = false)
    private BigDecimal quantity;

    @Column(length = 200)
    private String reason;

    @Column(name = "user_id")
    private UUID userId;
}
