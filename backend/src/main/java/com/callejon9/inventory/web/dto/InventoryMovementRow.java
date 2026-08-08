package com.callejon9.inventory.web.dto;

import com.callejon9.inventory.domain.InventoryMovementType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Una fila del ledger tal como la lee una persona. {@code userName} puede ser
 * nulo -- la llave es ON DELETE SET NULL y el usuario puede estar dado de
 * baja -- asi que viaja en su tipo de referencia.
 */
public record InventoryMovementRow(
        UUID id,
        UUID inventoryItemId,
        String itemName,
        String unit,
        InventoryMovementType movementType,
        BigDecimal quantity,
        String reason,
        String userName,
        Instant createdAt) {
}
