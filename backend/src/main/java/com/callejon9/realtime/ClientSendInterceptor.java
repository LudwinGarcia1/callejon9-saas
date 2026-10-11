package com.callejon9.realtime;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/**
 * Solo el servidor publica en los topicos del broker. El broker simple
 * reenvia a los suscriptores cualquier mensaje de cliente cuyo destino sea
 * suyo, asi que, sin este interceptor, cualquier usuario autenticado podria
 * inyectar pedidos falsos en la cocina de su restaurante o, conociendo el
 * UUID, en la de otro. {@link TenantSubscriptionInterceptor} no lo evita:
 * solo revisa SUBSCRIBE.
 *
 * <p>La regla se aplica al tipo de mensaje que el broker reenvia
 * ({@link SimpMessageType#MESSAGE}), no al comando STOMP: para Spring, una
 * trama MESSAGE escrita a mano por un cliente es del mismo tipo que un SEND y
 * el broker la reenvia igual. Revisar solo SEND dejaria esa puerta abierta.
 *
 * <p>Un mensaje de cliente solo se acepta hacia el prefijo de aplicacion
 * ({@value WebSocketConfig#APPLICATION_DESTINATION_PREFIX}), que va a
 * metodos {@code @MessageMapping} y nunca directo al broker. Hoy no existe
 * ninguno; el que se agregue debe validar el tenant del Principal por su
 * cuenta. Cualquier otro destino, o ninguno, se rechaza con un ERROR y el
 * cierre de la conexion. La publicacion del servidor
 * ({@link KitchenRealtimeEventPublisher}) no pasa por este canal.
 */
@Component
public class ClientSendInterceptor implements ChannelInterceptor {

    private static final String APPLICATION_PREFIX = WebSocketConfig.APPLICATION_DESTINATION_PREFIX + "/";

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        if (!SimpMessageType.MESSAGE.equals(SimpMessageHeaderAccessor.getMessageType(message.getHeaders()))) {
            return message;
        }

        String destination = SimpMessageHeaderAccessor.getDestination(message.getHeaders());
        if (destination == null || !destination.startsWith(APPLICATION_PREFIX)) {
            throw new AccessDeniedException(
                    "Publicacion rechazada: los clientes no pueden publicar en " + destination);
        }
        return message;
    }
}
