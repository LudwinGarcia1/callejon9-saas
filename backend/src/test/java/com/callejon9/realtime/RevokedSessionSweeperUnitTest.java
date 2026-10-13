package com.callejon9.realtime;

import com.callejon9.auth.service.AccessTokenVerifier;
import com.callejon9.user.domain.UserRole;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.WebSocketSession;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Casos del barrido que no se pueden provocar con un servidor real: el
 * contenedor fallando al cerrar una conexion o la base sin responder.
 *
 * <p>Usa dobles escritos a mano en vez de Mockito: el mock maker en linea no
 * puede instrumentar clases concretas en todas las versiones de JDK en las
 * que corre la suite.
 */
@DisplayName("Barrido de sesiones revocadas ante fallos")
class RevokedSessionSweeperUnitTest {

    private final List<String> closed = new ArrayList<>();

    @Test
    @DisplayName("si cerrar una conexion falla, las demas revocadas se cierran igual")
    void aFailingCloseDoesNotStopTheRest() {
        UUID tenantId = UUID.randomUUID();
        var sweeper = sweeper(
                List.of(connection(session("falla", true), tenantId),
                        connection(session("sana", false), tenantId)),
                Set::of);

        sweeper.closeRevokedConnections();

        assertThat(closed).containsExactly("falla", "sana");
    }

    @Test
    @DisplayName("si la base no responde, no se cierra ninguna conexion")
    void anUnavailableDatabaseClosesNothing() {
        UUID tenantId = UUID.randomUUID();
        var sweeper = sweeper(
                List.of(connection(session("abierta", false), tenantId)),
                () -> { throw new IllegalStateException("base no disponible"); });

        sweeper.closeRevokedConnections();

        assertThat(closed).isEmpty();
    }

    private RevokedSessionSweeper sweeper(
            List<OpenConnectionRegistry.OpenConnection> open, Supplier<Set<UUID>> activeSessions) {
        OpenConnectionRegistry registry = new OpenConnectionRegistry() {
            @Override
            public Collection<OpenConnection> snapshot() {
                return open;
            }
        };
        AccessTokenVerifier verifier = new AccessTokenVerifier(null, null, null) {
            @Override
            public Set<UUID> activeSessions(UUID tenantId, Set<UUID> sessionIds) {
                return activeSessions.get();
            }
        };
        return new RevokedSessionSweeper(registry, verifier);
    }

    /** Sesion que anota su cierre; con {@code failOnClose} ademas lanza, como un contenedor en apuros. */
    private WebSocketSession session(String id, boolean failOnClose) {
        return (WebSocketSession) Proxy.newProxyInstance(
                WebSocketSession.class.getClassLoader(), new Class<?>[] {WebSocketSession.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getId" -> id;
                    case "close" -> {
                        assertThat(args).containsExactly(RevokedSessionSweeper.SESSION_REVOKED);
                        closed.add(id);
                        if (failOnClose) {
                            throw new IllegalStateException("el contenedor ya la estaba cerrando");
                        }
                        yield null;
                    }
                    case "toString" -> "WebSocketSession[" + id + "]";
                    case "hashCode" -> id.hashCode();
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private static OpenConnectionRegistry.OpenConnection connection(WebSocketSession session, UUID tenantId) {
        return new OpenConnectionRegistry.OpenConnection(session, new AuthenticatedPrincipal(
                UUID.randomUUID(), tenantId, UserRole.KITCHEN, UUID.randomUUID()));
    }
}
