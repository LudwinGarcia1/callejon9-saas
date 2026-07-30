package com.callejon9.realtime;

import com.callejon9.auth.service.JwtService;
import com.callejon9.tenancy.TenantFilter;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

/**
 * Autentica el handshake de WebSocket con la misma cookie que usa
 * {@link TenantFilter} para HTTP. Sin ella (ausente o invalida) la conexion
 * se rechaza aqui mismo, antes de que el protocolo STOMP siquiera comience:
 * no hay "conexion anonima" en el canal en tiempo real como si la hay,
 * momentaneamente, en HTTP.
 */
@Component
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

    /** Bajo esta clave el Principal queda disponible para PrincipalHandshakeHandler. */
    static final String ATTR_PRINCIPAL = "authenticatedPrincipal";

    private final JwtService jwtService;

    public JwtHandshakeInterceptor(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes) {
        Optional<String> token = readAccessTokenCookie(request);
        if (token.isEmpty()) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }

        try {
            JwtService.TokenClaims claims = jwtService.parse(token.get());
            attributes.put(ATTR_PRINCIPAL,
                    new AuthenticatedPrincipal(claims.userId(), claims.tenantId(), claims.role()));
            return true;
        } catch (RuntimeException tokenIsNotUsable) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Exception exception) {
        // Nada que hacer: la unica responsabilidad de este interceptor es la
        // autenticacion previa al handshake.
    }

    private Optional<String> readAccessTokenCookie(ServerHttpRequest request) {
        if (!(request instanceof ServletServerHttpRequest servletRequest)) {
            return Optional.empty();
        }
        HttpServletRequest httpRequest = servletRequest.getServletRequest();
        Cookie[] cookies = httpRequest.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        return Arrays.stream(cookies)
                .filter(cookie -> TenantFilter.ACCESS_TOKEN_COOKIE.equals(cookie.getName()))
                .map(Cookie::getValue)
                .filter(value -> !value.isBlank())
                .findFirst();
    }
}
