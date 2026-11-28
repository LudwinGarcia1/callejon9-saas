package com.callejon9.order.event;

import com.callejon9.order.web.dto.WaiterCallResponse;
import java.util.UUID;

/**
 * Se publica cuando un comensal toca «Llamar al mesero» desde el menu QR o la
 * app de clientes, para que el canal en tiempo real (com.callejon9.realtime)
 * avise al personal del tenant correspondiente.
 */
public record WaiterCalledEvent(UUID tenantId, WaiterCallResponse call) {
}
