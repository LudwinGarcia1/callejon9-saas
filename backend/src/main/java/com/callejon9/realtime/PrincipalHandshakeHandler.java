package com.callejon9.realtime;

import java.security.Principal;
import java.util.Map;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

/**
 * Convierte el Principal que {@link JwtHandshakeInterceptor} dejo en los
 * atributos del handshake en el Principal real de la sesion STOMP.
 */
@Component
public class PrincipalHandshakeHandler extends DefaultHandshakeHandler {

    @Override
    protected Principal determineUser(
            ServerHttpRequest request, WebSocketHandler wsHandler, Map<String, Object> attributes) {
        Object principal = attributes.get(JwtHandshakeInterceptor.ATTR_PRINCIPAL);
        return principal instanceof AuthenticatedPrincipal authenticated ? authenticated : null;
    }
}
