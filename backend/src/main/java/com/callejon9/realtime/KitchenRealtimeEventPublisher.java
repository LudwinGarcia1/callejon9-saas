package com.callejon9.realtime;

import com.callejon9.kitchen.event.KitchenItemStatusChangedEvent;
import com.callejon9.order.event.OrderSentToKitchenEvent;
import java.util.UUID;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Puente entre los eventos de dominio de cocina y el topico STOMP del tenant.
 *
 * Escucha en AFTER_COMMIT: si la transaccion que envio la orden a cocina (o
 * que avanzo el estado de un producto) se revierte, este listener nunca se
 * ejecuta y el tablero de cocina jamas ve un estado que despues desaparecio.
 */
@Component
public class KitchenRealtimeEventPublisher {

    private final SimpMessagingTemplate messagingTemplate;

    public KitchenRealtimeEventPublisher(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderSentToKitchen(OrderSentToKitchenEvent event) {
        messagingTemplate.convertAndSend(kitchenTopicFor(event.tenantId()), event.order());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onKitchenItemStatusChanged(KitchenItemStatusChangedEvent event) {
        messagingTemplate.convertAndSend(kitchenTopicFor(event.tenantId()), event.item());
    }

    private String kitchenTopicFor(UUID tenantId) {
        return "/topic/tenant." + tenantId + ".kitchen";
    }
}
