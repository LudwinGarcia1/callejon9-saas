package com.callejon9.realtime;

import com.callejon9.support.TestSessions;
import com.callejon9.tenancy.TenantContext;
import com.callejon9.user.domain.User;
import com.callejon9.user.domain.UserRole;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
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
 * Revocar una sesion corta sus conexiones en tiempo real, no solo el
 * siguiente handshake. Corre sobre un servidor real con un ciclo de revision
 * de un segundo: si la tarea programada no estuviera conectada, ninguna
 * conexion se cerraria y las pruebas fallarian por tiempo.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "app.realtime.session-check-interval=PT1S")
@ActiveProfiles("test")
@DisplayName("Cierre de conexiones WebSocket de sesiones revocadas")
class RevokedSessionSweeperTest {

    private static final String SLUG_PREFIX = "realtime-revocacion";
    /** Holgura sobre el ciclo de 1 s para maquinas lentas. */
    private static final long CLOSE_DEADLINE_MS = 6_000;

    @LocalServerPort private int port;
    @Autowired private TestSessions testSessions;
    @Autowired private TestRestTemplate restTemplate;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private OpenConnectionRegistry registry;

    @AfterEach
    void cleanUp() {
        TenantContext.clear();
        testSessions.deleteTenants(SLUG_PREFIX);
    }

    @Test
    @DisplayName("el logout cierra las conexiones de esa sesion y deja vivas las demas")
    void logoutClosesOnlyTheConnectionsOfThatSession() throws Exception {
        User kitchen = testSessions.newUser(SLUG_PREFIX, UserRole.KITCHEN);
        User waiter = testSessions.newUserIn(kitchen.getTenantId(), UserRole.WAITER);
        String sharedScreen = testSessions.accessTokenFor(kitchen);
        String kitchenPhone = testSessions.accessTokenFor(kitchen);
        String waiterTablet = testSessions.accessTokenFor(waiter);

        StompSession revoked = connect(sharedScreen);
        StompSession sameUserOtherSession = connect(kitchenPhone);
        StompSession otherUser = connect(waiterTablet);

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, "access_token=" + sharedScreen);
        var logout = restTemplate.exchange("/api/v1/auth/logout", HttpMethod.POST,
                new HttpEntity<>(headers), Void.class);
        assertThat(logout.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        awaitDisconnection(revoked);

        // Al menos dos ciclos mas: las otras sesiones siguen conectadas.
        Thread.sleep(2_500);
        assertThat(sameUserOtherSession.isConnected())
                .as("otra sesion del mismo usuario no se toca").isTrue();
        assertThat(otherUser.isConnected())
                .as("las sesiones de otros usuarios no se tocan").isTrue();
    }

    @Test
    @DisplayName("una revocacion hecha en otra instancia (solo en la base) tambien corta la conexion")
    void revocationWrittenByAnotherInstanceIsDetected() throws Exception {
        User admin = testSessions.newUser(SLUG_PREFIX, UserRole.ADMIN);
        StompSession connection = connect(testSessions.accessTokenFor(admin));

        // Otra instancia no avisa a esta: solo deja la sesion revocada en la base.
        TenantContext.callAs(admin.getTenantId(), () -> transactionTemplate.execute(status ->
                jdbcTemplate.update("UPDATE refresh_tokens SET revoked_at = now() "
                        + "WHERE user_id = ? AND revoked_at IS NULL", admin.getId())));

        awaitDisconnection(connection);
    }

    @Test
    @DisplayName("una conexion cerrada sale del registro de la instancia")
    void closedConnectionsLeaveTheRegistry() throws Exception {
        User admin = testSessions.newUser(SLUG_PREFIX, UserRole.ADMIN);
        UUID tenantId = admin.getTenantId();
        StompSession connection = connect(testSessions.accessTokenFor(admin));
        assertThat(connectionsOf(tenantId)).isEqualTo(1);

        connection.disconnect();

        long deadline = System.currentTimeMillis() + CLOSE_DEADLINE_MS;
        while (connectionsOf(tenantId) > 0 && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }
        assertThat(connectionsOf(tenantId)).isZero();
    }

    private long connectionsOf(UUID tenantId) {
        return registry.snapshot().stream()
                .filter(connection -> connection.principal().tenantId().equals(tenantId))
                .count();
    }

    private StompSession connect(String accessToken) throws Exception {
        WebSocketStompClient stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        WebSocketHttpHeaders handshakeHeaders = new WebSocketHttpHeaders();
        handshakeHeaders.add(HttpHeaders.COOKIE, "access_token=" + accessToken);

        CompletableFuture<StompSession> connected = new CompletableFuture<>();
        stompClient.connectAsync("ws://localhost:" + port + "/ws", handshakeHeaders,
                new StompSessionHandlerAdapter() {
                    @Override
                    public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
                        connected.complete(session);
                    }
                });
        return connected.get(10, TimeUnit.SECONDS);
    }

    private void awaitDisconnection(StompSession session) throws InterruptedException {
        long deadline = System.currentTimeMillis() + CLOSE_DEADLINE_MS;
        while (session.isConnected() && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }
        assertThat(session.isConnected())
                .as("el servidor debio cerrar la conexion de la sesion revocada")
                .isFalse();
    }
}
