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
 * Una fila del ledger. Aplicar en orden el efecto con signo de todos los
 * movimientos de un insumo -- ver {@link InventoryMovementType#signedEffect} --
 * reproduce siempre su columna stock: no existe ningun camino que cambie el
 * stock sin dejar una fila aqui.
 *
 * No es la suma cruda de quantity: la columna guarda la cantidad sin signo
 * salvo en ADJUSTMENT, y es el tipo el que dice la direccion.
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
