package com.callejon9.realtime;

import com.callejon9.kitchen.event.KitchenItemStatusChangedEvent;
import com.callejon9.kitchen.web.dto.KitchenItemResponse;
import com.callejon9.support.TestSessions;
import com.callejon9.user.domain.User;
import com.callejon9.user.domain.UserRole;
import java.lang.reflect.Type;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Solo el servidor publica en el topico de cocina. Un cliente que manda un
 * SEND a {@code /topic/**} recibe un ERROR y pierde la conexion, y el mensaje
 * nunca llega a la cocina, sea de su restaurante o de otro.
 *
 * <p>Cada prueba espera a que el emisor reciba el ERROR o a que el mensaje
 * falso llegue a la cocina, lo que ocurra primero. El rechazo ocurre antes de
 * que el mensaje toque el broker, asi que cuando llega el ERROR el mensaje ya
 * no puede estar en camino: la prueba no depende de esperar un tiempo fijo.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DisplayName("Publicacion de clientes en el topico de cocina")
class KitchenTopicPublishTest {

    private static final String SLUG_PREFIX = "realtime-publicacion";
    private static final String FORGED = "{\"productName\":\"Pedido falso\",\"kitchenStatus\":\"READY\"}";
    private static final long TIMEOUT_SECONDS = 10;

    @LocalServerPort private int port;
    @Autowired private TestSessions testSessions;
    @Autowired private SimpMessagingTemplate messagingTemplate;
    @Autowired private KitchenRealtimeEventPublisher kitchenPublisher;

    @AfterEach
    void cleanUp() {
        testSessions.deleteTenants(SLUG_PREFIX);
    }

    @Test
    @DisplayName("P1: un usuario del mismo restaurante no puede publicar en su cocina")
    void sameTenantClientCannotPublishToItsKitchen() throws Exception {
        User kitchenUser = testSessions.newUser(SLUG_PREFIX, UserRole.KITCHEN);
        User waiter = testSessions.newUserIn(kitchenUser.getTenantId(), UserRole.WAITER);
        KitchenBoard board = KitchenBoard.open(this, kitchenUser);

        Client sender = connect(waiter);
        sender.session().send(board.topic(), FORGED.getBytes(StandardCharsets.UTF_8));

        assertRejected(sender.error(), sender.session()::isConnected, board);
    }

    /**
     * Para Spring, la trama MESSAGE (la que normalmente solo envia el
     * servidor) es del mismo tipo que SEND, y el broker la reenvia igual. Un
     * cliente STOMP no la manda nunca, asi que la prueba escribe las tramas a
     * mano sobre un WebSocket crudo.
     */
    @Test
    @DisplayName("una trama MESSAGE escrita a mano tampoco llega a la cocina")
    void rawMessageFrameCannotReachTheKitchen() throws Exception {
        User kitchenUser = testSessions.newUser(SLUG_PREFIX, UserRole.KITCHEN);
        User waiter = testSessions.newUserIn(kitchenUser.getTenantId(), UserRole.WAITER);
        KitchenBoard board = KitchenBoard.open(this, kitchenUser);

        RawClient sender = connectRaw(waiter);
        sender.session().sendMessage(new TextMessage("MESSAGE\ndestination:" + board.topic()
                + "\nsubscription:sub-0\nmessage-id:falso-1\ncontent-type:application/json\n\n"
                + FORGED + "\u0000"));

        assertRejected(sender.error(), sender.session()::isOpen, board);
    }

    @Test
    @DisplayName("P2: un usuario de otro restaurante no puede publicar en la cocina ajena")
    void otherTenantClientCannotPublishToForeignKitchen() throws Exception {
        User kitchenUser = testSessions.newUser(SLUG_PREFIX, UserRole.KITCHEN);
        User intruder = testSessions.newUser(SLUG_PREFIX, UserRole.ADMIN);
        assertThat(intruder.getTenantId()).isNotEqualTo(kitchenUser.getTenantId());
        KitchenBoard board = KitchenBoard.open(this, kitchenUser);

        Client sender = connect(intruder);
        sender.session().send(board.topic(), FORGED.getBytes(StandardCharsets.UTF_8));

        assertRejected(sender.error(), sender.session()::isConnected, board);
    }

    @Test
    @DisplayName("P3: la cocina sigue recibiendo los eventos que publica el servidor")
    void serverEventsStillReachTheKitchen() throws Exception {
        User kitchenUser = testSessions.newUser(SLUG_PREFIX, UserRole.KITCHEN);
        KitchenBoard board = KitchenBoard.open(this, kitchenUser);

        KitchenItemResponse item = new KitchenItemResponse(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "Tacos al pastor", 3, "PREPARING", null);
        kitchenPublisher.onKitchenItemStatusChanged(
                new KitchenItemStatusChangedEvent(kitchenUser.getTenantId(), item));

        String received = board.messages().poll(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertThat(received)
                .as("el evento del servidor debio llegar a la cocina")
                .contains("Tacos al pastor")
                .contains("PREPARING");
        assertThat(board.client().session().isConnected()).isTrue();
    }

    /**
     * Espera el ERROR del emisor o la llegada del mensaje falso. Despues
     * comprueba que la cocina sigue escuchando: un mensaje del servidor llega
     * y es el primero que recibe.
     */
    private void assertRejected(CompletableFuture<String> senderError, BooleanSupplier senderConnected,
                                KitchenBoard board) throws Exception {
        CompletableFuture.anyOf(senderError, board.forgedDelivered())
                .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

        assertThat(board.forgedDelivered().isDone())
                .as("el mensaje falso no debe llegar a la cocina")
                .isFalse();
        assertThat(senderError).isCompleted();
        awaitDisconnection(senderConnected);

        messagingTemplate.convertAndSend(board.topic(), "control");
        assertThat(board.messages().poll(TIMEOUT_SECONDS, TimeUnit.SECONDS))
                .as("la cocina sigue conectada y solo recibe lo que publica el servidor")
                .isEqualTo("control");
        assertThat(board.client().session().isConnected()).isTrue();
    }

    /** Tablero de cocina suscrito al topico de su restaurante. */
    private record KitchenBoard(Client client, String topic, BlockingQueue<String> messages,
                                CompletableFuture<Void> forgedDelivered) {

        static KitchenBoard open(KitchenTopicPublishTest test, User kitchenUser) throws Exception {
            Client client = test.connect(kitchenUser);
            String topic = "/topic/tenant." + kitchenUser.getTenantId() + ".kitchen";
            BlockingQueue<String> messages = new LinkedBlockingQueue<>();
            CompletableFuture<Void> forgedDelivered = new CompletableFuture<>();

            client.session().subscribe(topic, new StompFrameHandler() {
                @Override
                public Type getPayloadType(StompHeaders headers) {
                    return byte[].class;
                }

                @Override
                public void handleFrame(StompHeaders headers, Object payload) {
                    String body = new String((byte[]) payload, StandardCharsets.UTF_8);
                    if (FORGED.equals(body)) {
                        forgedDelivered.complete(null);
                    }
                    messages.add(body);
                }
            });

            awaitSubscription(test.messagingTemplate, topic, messages);
            return new KitchenBoard(client, topic, messages, forgedDelivered);
        }

        /**
         * El broker simple no confirma un SUBSCRIBE, asi que el servidor
         * publica una senal hasta que el tablero la recibe; despues se
         * descartan las senales sobrantes.
         */
        private static void awaitSubscription(SimpMessagingTemplate template, String topic,
                                              BlockingQueue<String> messages) throws InterruptedException {
            long deadline = System.currentTimeMillis() + TIMEOUT_SECONDS * 1_000;
            while (System.currentTimeMillis() < deadline) {
                template.convertAndSend(topic, "listo");
                if ("listo".equals(messages.poll(200, TimeUnit.MILLISECONDS))) {
                    Thread.sleep(200);
                    messages.removeIf("listo"::equals);
                    return;
                }
            }
            throw new AssertionError("el tablero no quedo suscrito a " + topic);
        }
    }

    private record Client(StompSession session, CompletableFuture<String> error) {
    }

    private Client connect(User user) throws Exception {
        WebSocketStompClient stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        WebSocketHttpHeaders handshakeHeaders = new WebSocketHttpHeaders();
        handshakeHeaders.add(HttpHeaders.COOKIE, "access_token=" + testSessions.accessTokenFor(user));

        CompletableFuture<StompSession> connected = new CompletableFuture<>();
        CompletableFuture<String> error = new CompletableFuture<>();
        stompClient.connectAsync("ws://localhost:" + port + "/ws", handshakeHeaders,
                new StompSessionHandlerAdapter() {
                    @Override
                    public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
                        connected.complete(session);
                    }

                    @Override
                    public void handleFrame(StompHeaders headers, Object payload) {
                        // Solo las tramas ERROR llegan al handler de sesion.
                        error.complete(headers.getFirst("message"));
                    }
                });
        return new Client(connected.get(TIMEOUT_SECONDS, TimeUnit.SECONDS), error);
    }

    private record RawClient(WebSocketSession session, CompletableFuture<String> error) {
    }

    /** WebSocket sin cliente STOMP: las tramas se escriben a mano. */
    private RawClient connectRaw(User user) throws Exception {
        WebSocketHttpHeaders handshakeHeaders = new WebSocketHttpHeaders();
        handshakeHeaders.add(HttpHeaders.COOKIE, "access_token=" + testSessions.accessTokenFor(user));

        CompletableFuture<Void> connected = new CompletableFuture<>();
        CompletableFuture<String> error = new CompletableFuture<>();
        WebSocketSession session = new StandardWebSocketClient().execute(new TextWebSocketHandler() {
            @Override
            protected void handleTextMessage(WebSocketSession session, TextMessage message) {
                String frame = message.getPayload();
                if (frame.startsWith("CONNECTED")) {
                    connected.complete(null);
                } else if (frame.startsWith("ERROR")) {
                    error.complete(frame);
                }
            }
        }, handshakeHeaders, URI.create("ws://localhost:" + port + "/ws")).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

        session.sendMessage(new TextMessage("CONNECT\naccept-version:1.2\nhost:localhost\n\n\u0000"));
        connected.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        return new RawClient(session, error);
    }

    private static void awaitDisconnection(BooleanSupplier connected) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5_000;
        while (connected.getAsBoolean() && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }
        assertThat(connected.getAsBoolean())
                .as("el servidor debio cerrar la conexion del emisor")
                .isFalse();
    }
}
