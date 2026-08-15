package com.callejon9.inventory.web.dto;

import com.callejon9.inventory.domain.InventoryMovement;
import com.callejon9.inventory.domain.InventoryMovementType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * El movimiento tal como quedo guardado. Importa devolverlo y no un 204: en un
 * ajuste, el delta y el motivo compuesto los produjo el servidor, y quien lo
 * registro necesita ver que quedo.
 */
public record RegisteredMovementResponse(
        UUID id,
        UUID inventoryItemId,
        InventoryMovementType movementType,
        BigDecimal quantity,
        String reason,
        Instant createdAt) {

    public static RegisteredMovementResponse from(InventoryMovement movement) {
        return new RegisteredMovementResponse(
                movement.getId(), movement.getInventoryItemId(),
                movement.getMovementType(), movement.getQuantity(),
                movement.getReason(), movement.getCreatedAt());
    }
}
