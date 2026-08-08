package com.callejon9.inventory.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * No incluye {@code stock} a proposito: el stock solo cambia a traves de un
 * movimiento, para que el ledger explique siempre el numero que se ve.
 */
public record UpdateInventoryItemRequest(
        @NotBlank @Size(max = 160) String name,
        @NotBlank @Size(max = 20) String unit,
        @PositiveOrZero BigDecimal minStock,
        @PositiveOrZero BigDecimal unitCost) {
}
