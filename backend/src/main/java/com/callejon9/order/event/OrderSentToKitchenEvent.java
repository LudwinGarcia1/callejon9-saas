package com.callejon9.order.event;

import com.callejon9.order.web.dto.OrderResponse;
import java.util.UUID;

/**
 * Se publica cuando una orden pasa a {@code SENT}, para que el canal en
 * tiempo real (com.callejon9.realtime) la anuncie al tablero de cocina del
 * tenant correspondiente.
 *
 * <p>El payload es un DTO ({@link OrderResponse}), nunca la entidad: el
 * canal en tiempo real no debe acoplarse a Hibernate ni exponer columnas que
 * no le correspondan.
 */
public record OrderSentToKitchenEvent(UUID tenantId, OrderResponse order) {
}
