package com.callejon9.realtime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * Reglas del interceptor sobre tramas armadas a mano. Cubre los bordes que
 * un cliente STOMP de Spring no deja enviar, como un SEND sin destino.
 */
@DisplayName("Guardia de SEND de clientes")
class ClientSendInterceptorTest {

    private static final String KITCHEN_TOPIC = "/topic/tenant.6f1c2a1e-0000-4000-8000-000000000001.kitchen";

    private final ClientSendInterceptor interceptor = new ClientSendInterceptor();
    private final MessageChannel channel = mock(MessageChannel.class);

    private static Message<byte[]> frame(StompCommand command, String destination) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        if (destination != null) {
            accessor.setDestination(destination);
        }
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    @ParameterizedTest
    @ValueSource(strings = {KITCHEN_TOPIC, "/topic/cualquiera", "/queue/x", "/user/queue/x", "/app", "/application/x", ""})
    @DisplayName("rechaza un SEND fuera del prefijo de aplicacion")
    void rejectsSendOutsideTheApplicationPrefix(String destination) {
        Message<byte[]> message = frame(StompCommand.SEND, destination);

        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("rechaza un SEND sin destino")
    void rejectsSendWithoutDestination() {
        Message<byte[]> message = frame(StompCommand.SEND, null);

        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("rechaza una trama MESSAGE de cliente: el broker la reenvia igual que un SEND")
    void rejectsClientMessageFrame() {
        Message<byte[]> message = frame(StompCommand.MESSAGE, KITCHEN_TOPIC);

        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("deja pasar un SEND al prefijo de aplicacion")
    void allowsSendToTheApplicationPrefix() {
        Message<byte[]> message = frame(StompCommand.SEND, "/app/kitchen.ping");

        assertThat(interceptor.preSend(message, channel)).isSameAs(message);
    }

    @Test
    @DisplayName("no interviene en otros comandos: SUBSCRIBE lo valida su propio guardia")
    void ignoresOtherCommands() {
        Message<byte[]> subscribe = frame(StompCommand.SUBSCRIBE, KITCHEN_TOPIC);
        Message<byte[]> disconnect = frame(StompCommand.DISCONNECT, null);

        assertThat(interceptor.preSend(subscribe, channel)).isSameAs(subscribe);
        assertThat(interceptor.preSend(disconnect, channel)).isSameAs(disconnect);
    }
}
