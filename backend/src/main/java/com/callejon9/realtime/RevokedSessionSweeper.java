package com.callejon9.realtime;

import com.callejon9.auth.service.AccessTokenVerifier;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;

/**
 * Cierra las conexiones WebSocket cuya sesion de login ya no esta vigente:
 * logout, deteccion de reutilizacion del refresh token, desactivacion del
 * usuario o vencimiento absoluto de la sesion.
 *
 * <p>El handshake solo verifica la sesion al conectar. Esta tarea la revisa
 * periodicamente contra {@code refresh_tokens}, que es la fuente de verdad de
 * todas las instancias: una revocacion hecha en otra instancia se detecta
 * igual, sin mensajeria entre ellas. La latencia maxima entre revocar y
 * cortar es {@code app.realtime.session-check-interval} (30 s por defecto)
 * mas lo que tarde la consulta.
 *
 * <p>Costo: una consulta por restaurante con conexiones abiertas en cada
 * ciclo, independiente del numero de mensajes y de la antiguedad de las
 * sesiones. No hay consulta por mensaje.
 *
 * <p>Corre en su propio hilo ({@link WebSocketConfig#SESSION_SWEEP_SCHEDULER}),
 * no en el scheduler del broker STOMP: una base lenta retrasa el barrido, no
 * los heartbeats ni la entrega de mensajes.
 */
@Component
public class RevokedSessionSweeper {

    /** Cierre por politica (1008): el cliente no debe reintentar con la misma sesion. */
    static final CloseStatus SESSION_REVOKED = CloseStatus.POLICY_VIOLATION.withReason("Sesion revocada");

    private static final Logger log = LoggerFactory.getLogger(RevokedSessionSweeper.class);

    private final OpenConnectionRegistry registry;
    private final AccessTokenVerifier accessTokenVerifier;

    public RevokedSessionSweeper(OpenConnectionRegistry registry, AccessTokenVerifier accessTokenVerifier) {
        this.registry = registry;
        this.accessTokenVerifier = accessTokenVerifier;
    }

    @Scheduled(scheduler = WebSocketConfig.SESSION_SWEEP_SCHEDULER,
            fixedDelayString = "${app.realtime.session-check-interval:PT30S}",
            initialDelayString = "${app.realtime.session-check-interval:PT30S}")
    public void closeRevokedConnections() {
        Map<UUID, List<OpenConnectionRegistry.OpenConnection>> byTenant = registry.snapshot().stream()
                .collect(Collectors.groupingBy(connection -> connection.principal().tenantId()));

        byTenant.forEach((tenantId, connections) -> {
            Set<UUID> sessionIds = connections.stream()
                    .map(connection -> connection.principal().sessionId())
                    .collect(Collectors.toSet());

            Set<UUID> active;
            try {
                active = accessTokenVerifier.activeSessions(tenantId, sessionIds);
            } catch (RuntimeException databaseUnavailable) {
                // Sin respuesta de la base no se decide nada: se reintenta en
                // el siguiente ciclo en vez de cortar conexiones validas.
                log.warn("No se pudieron revalidar las conexiones del tenant {}", tenantId,
                        databaseUnavailable);
                return;
            }

            connections.stream()
                    .filter(connection -> !active.contains(connection.principal().sessionId()))
                    .forEach(this::close);
        });
    }

    /**
     * Un fallo al cerrar una conexion no detiene el ciclo: las demas se
     * cierran igual, y esta se reintenta en el siguiente si sigue registrada.
     */
    private void close(OpenConnectionRegistry.OpenConnection connection) {
        try {
            connection.session().close(SESSION_REVOKED);
        } catch (IOException | RuntimeException closeFailed) {
            log.warn("No se pudo cerrar la conexion {} de una sesion revocada",
                    connection.session().getId(), closeFailed);
        }
    }
}
