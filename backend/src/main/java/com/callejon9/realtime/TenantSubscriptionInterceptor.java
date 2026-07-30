package com.callejon9.realtime;

import java.security.Principal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/**
 * El equivalente en tiempo real a la Row-Level Security de PostgreSQL: valida
 * en cada SUBSCRIBE que el tenant codificado en el destino coincide con el
 * tenant del Principal autenticado en el handshake (ver
 * {@link JwtHandshakeInterceptor}).
 *
 * Sin este interceptor, cualquier usuario autenticado de un restaurante
 * podria suscribirse al topico de cocina de otro simplemente adivinando su
 * tenantId — el mismo UUID que ya viaja, por ejemplo, en las respuestas de
 * la API. RLS protege las filas en la base de datos; esto protege los
 * mensajes en el canal.
 */
@Component
public class TenantSubscriptionInterceptor implements ChannelInterceptor {

    private static final Pattern KITCHEN_TOPIC =
            Pattern.compile("^/topic/tenant\\.([0-9a-fA-F-]{36})\\.kitchen$");

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        if (!StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            return message;
        }

        String destination = accessor.getDestination();
        Principal principal = accessor.getUser();

        if (destination == null || !(principal instanceof AuthenticatedPrincipal authenticated)) {
            throw new AccessDeniedException("Suscripcion rechazada: no hay sesion autenticada.");
        }

        Matcher matcher = KITCHEN_TOPIC.matcher(destination);
        if (!matcher.matches() || !matcher.group(1).equalsIgnoreCase(authenticated.tenantId().toString())) {
            throw new AccessDeniedException(
                    "Suscripcion rechazada: el destino " + destination
                            + " no corresponde al tenant autenticado.");
        }

        return message;
    }
}
