package com.callejon9.order.web.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Llamada al mesero que el canal en tiempo real entrega a meseros y caja: la
 * mesa que llamo y cuando. No lleva datos del comensal porque no los hay.
 */
public record WaiterCallResponse(UUID tableId, int tableNumber, Instant calledAt) {
}
