package com.callejon9.realtime;

import com.callejon9.auth.service.JwtService;
import com.callejon9.user.domain.User;
import com.callejon9.user.domain.UserRole;
import java.lang.reflect.Type;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prueba el equivalente en tiempo real de RLS: un usuario autenticado del
 * tenant A puede suscribirse al topico de cocina de SU tenant sin
 * problemas, pero el servidor rechaza la suscripcion (trama STOMP ERROR y
 * cierre de la conexion) cuando intenta suscribirse al topico del tenant B.
 *
 * <p>No hace falta persistir tenants ni usuarios: el handshake solo lee y
 * valida el JWT (ver {@link JwtHandshakeInterceptor}), y
 * {@link TenantSubscriptionInterceptor} solo compara el tenantId del token
 * contra el destino solicitado.
 *
 * <p>El broker simple en memoria de Spring (enableSimpleBroker) nunca envia
 * RECEIPT para SUBSCRIBE (solo lo hace para DISCONNECT), asi que la prueba
 * no puede esperar un recibo de la suscripcion legitima; en vez de eso, deja
 * una ventana corta para que un rechazo indebido ya se hubiera manifestado
 * antes de intentar la suscripcion cruzada.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DisplayName("Aislamiento de tenants en el canal de tiempo real")
class TenantSubscriptionInterceptorTest {

    @LocalServerPort
    private int port;

    @Autowired
    private JwtService jwtService;

    private String tokenFor(UUID tenantId, UserRole role) {
        User user = User.builder()
                .email("realtime@test.com").passwordHash("x").fullName("Realtime")
                .role(role).active(true).build();
        user.setId(UUID.randomUUID());
        user.setTenantId(tenantId);
        return jwtService.generateAccessToken(user);
    }

    @Test
    @DisplayName("un usuario del tenant A no puede suscribirse al topico de cocina del tenant B")
    void userFromTenantACannotSubscribeToTenantBsKitchenTopic() throws Exception {
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();

        WebSocketStompClient stompClient = new WebSocketStompClient(new StandardWebSocketClient());

        WebSocketHttpHeaders handshakeHeaders = new WebSocketHttpHeaders();
        handshakeHeaders.add(HttpHeaders.COOKIE, "access_token=" + tokenFor(tenantA, UserRole.ADMIN));

        CompletableFuture<StompSession> connected = new CompletableFuture<>();
        CompletableFuture<String> errorReceived = new CompletableFuture<>();

        StompSessionHandlerAdapter handler = new StompSessionHandlerAdapter() {

            @Override
            public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
                connected.complete(session);
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                // DefaultStompSession enruta las tramas ERROR al handler de
                // sesion (no al de la suscripcion): cualquier invocacion
                // aqui solo puede venir de un ERROR del servidor. El texto
                // de la excepcion viaja en el encabezado STOMP "message" del
                // frame ERROR (StompHeaderAccessor.setMessage), no en el
                // body, que va vacio.
                errorReceived.complete(headers.getFirst("message"));
            }

            @Override
            public void handleTransportError(StompSession session, Throwable exception) {
                errorReceived.completeExceptionally(exception);
            }
        };

        stompClient.connectAsync("ws://localhost:" + port + "/ws", handshakeHeaders, handler);
        StompSession session = connected.get(10, TimeUnit.SECONDS);

        session.subscribe("/topic/tenant." + tenantA + ".kitchen", noopFrameHandler());

        // Ventana para que un rechazo indebido de la suscripcion LEGITIMA ya
        // se hubiera manifestado (ERROR + cierre de conexion) antes de
        // intentar la suscripcion cruzada.
        Thread.sleep(500);
        assertThat(errorReceived.isDone())
                .as("la suscripcion al propio tenant no debio ser rechazada")
                .isFalse();
        assertThat(session.isConnected()).isTrue();

        session.subscribe("/topic/tenant." + tenantB + ".kitchen", noopFrameHandler());

        // El servidor envuelve el AccessDeniedException de
        // TenantSubscriptionInterceptor en un MessageDeliveryException
        // generico antes de mandar el ERROR al cliente (el texto propio de
        // la excepcion no cruza el canal); lo que si prueba el rechazo es
        // que SI llega un ERROR y que, acto seguido, el servidor cierra la
        // conexion (StompSubProtocolHandler.sendErrorMessage).
        String errorMessage = errorReceived.get(10, TimeUnit.SECONDS);
        assertThat(errorMessage).isNotBlank();

        awaitDisconnection(session);
    }

    private void awaitDisconnection(StompSession session) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5_000;
        while (session.isConnected() && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }
        assertThat(session.isConnected())
                .as("el servidor debio cerrar la conexion tras rechazar la suscripcion")
                .isFalse();
    }

    private StompFrameHandler noopFrameHandler() {
        return new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return byte[].class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                // No se espera ningun mensaje en este topico durante la prueba.
            }
        };
    }
}
