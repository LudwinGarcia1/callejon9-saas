package com.callejon9.order.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Los topes no son reglas de negocio finas: acotan lo que un cliente puede
 * empujar en una sola linea para que una cantidad o una nota desmedida no
 * desborde {@code orders.total} ni llegue completa al tablero de cocina.
 */
public record AddOrderItemRequest(
        @NotNull UUID productId,
        @NotNull @Min(1) @Max(99) Integer quantity,
        @Size(max = 500) String notes) {
}
