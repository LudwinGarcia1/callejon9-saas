package com.callejon9.realtime;

import com.callejon9.support.TestSessions;
import com.callejon9.table.domain.RestaurantTable;
import com.callejon9.table.domain.TableStatus;
import com.callejon9.table.repository.RestaurantTableRepository;
import com.callejon9.tenancy.TenantContext;
import com.callejon9.user.domain.User;
import com.callejon9.user.domain.UserRole;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * «Llamar al mesero» de punta a punta: el comensal llama sin sesion desde el
 * menu QR y el mesero de ese restaurante recibe el aviso de la mesa en su
 * topico de llamadas. El mesero de otro restaurante no recibe nada.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DisplayName("Llamada al mesero en tiempo real")
class WaiterCallTopicTest {

    private static final String SLUG_PREFIX = "realtime-llamada";
    private static final long TIMEOUT_SECONDS = 10;

    @LocalServerPort private int port;
    @Autowired private TestSessions testSessions;
    @Autowired private SimpMessagingTemplate messagingTemplate;
    @Autowired private RestaurantTableRepository tableRepository;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        testSessions.deleteTenants(SLUG_PREFIX);
    }

    @Test
    @DisplayName("el mesero del restaurante recibe la mesa que llamo; el de otro restaurante no")
    void waiterOfTheTenantReceivesTheCall() throws Exception {
        User waiterA = testSessions.newUser(SLUG_PREFIX, UserRole.WAITER);
        User waiterB = testSessions.newUser(SLUG_PREFIX, UserRole.WAITER);
        UUID tableId = createTable(waiterA.getTenantId(), 7);
        String slugA = jdbcTemplate.queryForObject(
                "SELECT slug FROM tenants WHERE id = ?", String.class, waiterA.getTenantId());

        BlockingQueue<String> callsA = subscribe(waiterA);
        BlockingQueue<String> callsB = subscribe(waiterB);

        HttpResponse<String> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create(
                                "http://localhost:" + port + "/api/v1/public/" + slugA + "/tables/7/call-waiter"))
                        .POST(HttpRequest.BodyPublishers.noBody())
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);

        String received = callsA.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertThat(received)
                .as("el mesero del restaurante debio recibir la llamada")
                .contains("\"tableNumber\":7")
                .contains(tableId.toString())
                .contains("calledAt");

        // El aviso de A ya llego; si se hubiera enviado tambien a B, estaria ahi.
        assertThat(callsB.poll(500, TimeUnit.MILLISECONDS))
                .as("el mesero de otro restaurante no recibe la llamada")
                .isNull();
    }

    private UUID createTable(UUID tenantId, int number) {
        return TenantContext.callAs(tenantId, () -> transactionTemplate.execute(status ->
                tableRepository.save(RestaurantTable.builder()
                        .number(number)
                        .capacity(4)
                        .status(TableStatus.FREE)
                        .active(true)
                        .build()).getId()));
    }

    /** Conecta al usuario y lo deja suscrito al topico de llamadas de su restaurante. */
    private BlockingQueue<String> subscribe(User user) throws Exception {
        WebSocketStompClient stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        WebSocketHttpHeaders handshakeHeaders = new WebSocketHttpHeaders();
        handshakeHeaders.add(HttpHeaders.COOKIE, "access_token=" + testSessions.accessTokenFor(user));

        CompletableFuture<StompSession> connected = new CompletableFuture<>();
        stompClient.connectAsync("ws://localhost:" + port + "/ws", handshakeHeaders,
                new StompSessionHandlerAdapter() {
                    @Override
                    public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
                        connected.complete(session);
                    }
                });
        StompSession session = connected.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

        String topic = WaiterCallRealtimeEventPublisher.waiterCallsTopicFor(user.getTenantId());
        BlockingQueue<String> messages = new LinkedBlockingQueue<>();
        session.subscribe(topic, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return byte[].class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                messages.add(new String((byte[]) payload, StandardCharsets.UTF_8));
            }
        });
        awaitSubscription(topic, messages);
        return messages;
    }

    /**
     * El broker simple no confirma un SUBSCRIBE: el servidor publica una
     * senal hasta que llega y despues se descartan las sobrantes.
     */
    private void awaitSubscription(String topic, BlockingQueue<String> messages) throws InterruptedException {
        long deadline = System.currentTimeMillis() + TIMEOUT_SECONDS * 1_000;
        while (System.currentTimeMillis() < deadline) {
            messagingTemplate.convertAndSend(topic, "listo");
            if ("listo".equals(messages.poll(200, TimeUnit.MILLISECONDS))) {
                Thread.sleep(200);
                messages.removeIf("listo"::equals);
                return;
            }
        }
        throw new AssertionError("no quedo suscrito a " + topic);
    }
}
