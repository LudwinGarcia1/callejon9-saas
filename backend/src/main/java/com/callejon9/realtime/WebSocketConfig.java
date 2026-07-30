package com.callejon9.realtime;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Canal en tiempo real STOMP sobre WebSocket, expuesto en {@code /ws}.
 *
 * El fallback SockJS se deja deliberadamente sin activar (no se llama
 * {@code .withSockJS()}): el cliente objetivo de este canal (tablero de
 * cocina, apps de meseros) soporta WebSocket nativo, y anadir el fallback
 * solo agregaria superficie sin necesidad real en este dominio.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtHandshakeInterceptor jwtHandshakeInterceptor;
    private final PrincipalHandshakeHandler principalHandshakeHandler;
    private final TenantSubscriptionInterceptor tenantSubscriptionInterceptor;

    public WebSocketConfig(
            JwtHandshakeInterceptor jwtHandshakeInterceptor,
            PrincipalHandshakeHandler principalHandshakeHandler,
            TenantSubscriptionInterceptor tenantSubscriptionInterceptor) {
        this.jwtHandshakeInterceptor = jwtHandshakeInterceptor;
        this.principalHandshakeHandler = principalHandshakeHandler;
        this.tenantSubscriptionInterceptor = tenantSubscriptionInterceptor;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setHandshakeHandler(principalHandshakeHandler)
                .addInterceptors(jwtHandshakeInterceptor)
                .setAllowedOriginPatterns("*");
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(tenantSubscriptionInterceptor);
    }
}
