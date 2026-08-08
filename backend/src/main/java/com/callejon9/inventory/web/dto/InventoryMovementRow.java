package com.callejon9.inventory.web.dto;

import com.callejon9.inventory.domain.InventoryMovementType;
import com.fasterxml.jackson.annotation.JsonProperty;
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

    /**
     * El efecto sobre el stock, ya con su signo: una salida de 5 viaja como
     * -5. Va calculado desde aqui y no en el cliente por el mismo motivo que
     * {@code level} en InventoryItemResponse -- la interfaz pinta lo que
     * recibe en vez de reimplementar una regla de negocio.
     *
     * Sin esto, la interfaz mostraria "+5" en una salida, porque la columna
     * quantity guarda la cantidad sin signo salvo en ADJUSTMENT y quien sabe
     * la direccion de cada tipo es {@link InventoryMovementType}.
     */
    @JsonProperty("signedQuantity")
    public BigDecimal signedQuantity() {
        return movementType.signedEffect(quantity);
    }
}
