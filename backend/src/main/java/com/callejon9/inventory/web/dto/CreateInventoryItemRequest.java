package com.callejon9.inventory.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * {@code minStock} y {@code unitCost} son opcionales y su ausencia equivale a
 * cero, que es el DEFAULT de las columnas. Un insumo sin minimo configurado no
 * genera alertas, que es lo que se espera de no haberlo configurado.
 *
 * {@code initialStock} tambien es opcional y su ausencia equivale a cero: dar
 * de alta un insumo que todavia no llega es el caso normal, no la excepcion.
 */
public record CreateInventoryItemRequest(
        @NotBlank @Size(max = 160) String name,
        @NotBlank @Size(max = 20) String unit,
        @PositiveOrZero BigDecimal minStock,
        @PositiveOrZero BigDecimal unitCost,
        @PositiveOrZero BigDecimal initialStock) {
}
