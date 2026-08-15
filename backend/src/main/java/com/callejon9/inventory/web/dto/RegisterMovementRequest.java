package com.callejon9.inventory.web.dto;

import com.callejon9.inventory.domain.InventoryMovementType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Dos campos de cantidad, cada uno con un solo significado:
 *
 * <pre>
 * IN / OUT / WASTE    { movementType, quantity: &gt; 0, reason }
 * ADJUSTMENT          { movementType, countedStock: &gt;= 0, reason }
 * </pre>
 *
 * El delta de un ajuste lo calcula el servidor, no el cliente: un cliente
 * leeria un stock que quizas ya cambio y dejaria el inventario en un numero
 * que nadie conto.
 *
 * {@code reason} se limita a 150 caracteres para que el prefijo "Conteo
 * fisico: ..." quepa en el varchar(200) de la columna.
 */
@ValidMovementRequest
public record RegisterMovementRequest(
        @NotNull UUID inventoryItemId,
        @NotNull InventoryMovementType movementType,
        @Positive BigDecimal quantity,
        @PositiveOrZero BigDecimal countedStock,
        @Size(max = 150) String reason) {
}
