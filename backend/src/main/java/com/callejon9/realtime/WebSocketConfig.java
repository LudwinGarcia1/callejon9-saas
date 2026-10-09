package com.callejon9.realtime;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;

/**
 * Canal en tiempo real STOMP sobre WebSocket, expuesto en {@code /ws}.
 *
 * El fallback SockJS se deja deliberadamente sin activar (no se llama
 * {@code .withSockJS()}): el cliente objetivo de este canal (tablero de
 * cocina, apps de meseros) soporta WebSocket nativo, y anadir el fallback
 * solo agregaria superficie sin necesidad real en este dominio.
 *
 * {@code @EnableScheduling} activa {@link RevokedSessionSweeper}, que cierra
 * las conexiones de sesiones revocadas despues del handshake.
 */
@Configuration
@EnableScheduling
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtHandshakeInterceptor jwtHandshakeInterceptor;
    private final PrincipalHandshakeHandler principalHandshakeHandler;
    private final TenantSubscriptionInterceptor tenantSubscriptionInterceptor;
    private final OpenConnectionRegistry openConnectionRegistry;
    private final String[] allowedOrigins;

    public WebSocketConfig(
            JwtHandshakeInterceptor jwtHandshakeInterceptor,
            PrincipalHandshakeHandler principalHandshakeHandler,
            TenantSubscriptionInterceptor tenantSubscriptionInterceptor,
            OpenConnectionRegistry openConnectionRegistry) {
            OpenConnectionRegistry openConnectionRegistry,
            @Value("${app.realtime.allowed-origins}") String[] allowedOrigins) {
        this.jwtHandshakeInterceptor = jwtHandshakeInterceptor;
        this.principalHandshakeHandler = principalHandshakeHandler;
        this.tenantSubscriptionInterceptor = tenantSubscriptionInterceptor;
        this.openConnectionRegistry = openConnectionRegistry;
        this.allowedOrigins = requireExplicitOrigins(allowedOrigins);
    }

    /** Nombre del scheduler propio de {@link RevokedSessionSweeper}. */
    public static final String SESSION_SWEEP_SCHEDULER = "realtimeSessionSweepScheduler";

    /**
     * Un hilo dedicado para el barrido de sesiones revocadas. Sin el, la tarea
     * correria en el scheduler del broker STOMP y una consulta lenta
     * retrasaria sus heartbeats.
     */
    @Bean(name = SESSION_SWEEP_SCHEDULER)
    public ThreadPoolTaskScheduler realtimeSessionSweepScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("ws-session-sweep-");
        return scheduler;
    }

    /** Registra cada conexion para que {@link RevokedSessionSweeper} pueda cerrarla. */
    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration.addDecoratorFactory(openConnectionRegistry);
    }

    /**
     * El handshake se autentica con la cookie, y el navegador la adjunta sin
     * importar que pagina abre el socket. La lista de origenes es lo que
     * impide que otro sitio abra el canal con la sesion del usuario (Cross-Site
     * WebSocket Hijacking), sin depender solo de SameSite=Strict.
     *
     * <p>Un handshake sin cabecera Origin (cliente nativo, no navegador) no se
     * ve afectado: Spring lo trata como mismo origen y sigue necesitando la
     * cookie.
     */
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setHandshakeHandler(principalHandshakeHandler)
                .addInterceptors(jwtHandshakeInterceptor)
                .setAllowedOrigins(allowedOrigins);
    }

    /** Falla al arrancar antes que abrir el canal a cualquier origen por un error de configuracion. */
    static String[] requireExplicitOrigins(String[] origins) {
        if (origins == null || origins.length == 0) {
            throw new IllegalStateException("app.realtime.allowed-origins no puede estar vacio");
        }
        for (String origin : origins) {
            if (origin == null || origin.isBlank() || origin.contains("*")) {
                throw new IllegalStateException(
                        "app.realtime.allowed-origins solo acepta origenes explicitos, sin comodines: " + origin);
            }
        }
        return origins;
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
