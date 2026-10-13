package com.callejon9.realtime;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.WebSocketHandlerDecorator;
import org.springframework.web.socket.handler.WebSocketHandlerDecoratorFactory;

/**
 * Conexiones WebSocket abiertas en ESTA instancia del backend, con el
 * Principal autenticado en el handshake. Solo sirve para encontrar la
 * conexion que hay que cerrar: la decision de cerrarla sale de la base (ver
 * {@link RevokedSessionSweeper}), que es lo que la hace valida con varias
 * instancias.
 *
 * <p>Se registra como decorador del handler de WebSocket, asi que ve cada
 * conexion al abrirse y al cerrarse, sea quien sea el que la cierre.
 */
@Component
public class OpenConnectionRegistry implements WebSocketHandlerDecoratorFactory {

    /** Una conexion abierta y la sesion de login con la que se autentico. */
    public record OpenConnection(WebSocketSession session, AuthenticatedPrincipal principal) {
    }

    private final Map<String, OpenConnection> connections = new ConcurrentHashMap<>();

    @Override
    public WebSocketHandler decorate(WebSocketHandler handler) {
        return new WebSocketHandlerDecorator(handler) {

            @Override
            public void afterConnectionEstablished(WebSocketSession session) throws Exception {
                if (session.getPrincipal() instanceof AuthenticatedPrincipal principal) {
                    connections.put(session.getId(), new OpenConnection(session, principal));
                }
                super.afterConnectionEstablished(session);
            }

            @Override
            public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus)
                    throws Exception {
                connections.remove(session.getId());
                super.afterConnectionClosed(session, closeStatus);
            }
        };
    }

    /** Copia de las conexiones abiertas en este momento. */
    public Collection<OpenConnection> snapshot() {
        return List.copyOf(connections.values());
    }
}
