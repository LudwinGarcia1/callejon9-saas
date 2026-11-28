package com.callejon9.realtime;

import com.callejon9.order.event.WaiterCalledEvent;
import java.util.UUID;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Entrega las llamadas al mesero en el topico de llamadas del tenant, al que
 * se suscriben las pantallas de mesero. Es un topico aparte del de cocina:
 * el tablero de cocina no debe recibir estos avisos ni los meseros
 * interpretarlos como eventos de una orden.
 *
 * <p>Escucha en AFTER_COMMIT, igual que {@link KitchenRealtimeEventPublisher}:
 * si la transaccion de la llamada falla, el aviso no sale.
 */
@Component
public class WaiterCallRealtimeEventPublisher {

    private final SimpMessagingTemplate messagingTemplate;

    public WaiterCallRealtimeEventPublisher(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onWaiterCalled(WaiterCalledEvent event) {
        messagingTemplate.convertAndSend(waiterCallsTopicFor(event.tenantId()), event.call());
    }

    static String waiterCallsTopicFor(UUID tenantId) {
        return "/topic/tenant." + tenantId + ".waiter-calls";
    }
}
