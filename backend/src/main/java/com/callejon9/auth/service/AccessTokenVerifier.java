package com.callejon9.auth.service;

import com.callejon9.auth.repository.RefreshTokenRepository;
import com.callejon9.tenancy.TenantContext;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Decide si un access token autoriza una peticion. Lo usan HTTP
 * ({@code TenantFilter}) y el handshake de WebSocket, para que ambos canales
 * apliquen exactamente la misma regla.
 *
 * <p>Un JWT valido no basta: ademas su sesion ({@code sid}) tiene que seguir
 * vigente en el servidor. Sin esa consulta, un logout solo borraria la cookie
 * del navegador y una copia del token seguiria abriendo la API hasta expirar.
 * La consulta corre con el tenant firmado del propio token, asi que RLS
 * impide que un {@code sid} ajeno encuentre la sesion de otro restaurante.
 *
 * <p>Cuesta una lectura indexada por peticion autenticada. Se prefiere a una
 * cache en memoria porque la revocacion tiene que verse igual desde todas las
 * instancias del backend en el mismo momento.
 */
@Service
public class AccessTokenVerifier {

    /**
     * Tope de la revalidacion por lotes. Corre en segundo plano, lejos de
     * cualquier peticion, asi que ante una base lenta prefiere fallar pronto y
     * reintentar en el siguiente ciclo antes que quedarse esperando.
     */
    static final int ACTIVE_SESSIONS_TIMEOUT_SECONDS = 5;

    private final JwtService jwtService;
    private final RefreshTokenRepository refreshTokenRepository;
    private final TransactionTemplate readOnlyTransaction;
    private final TransactionTemplate boundedReadOnlyTransaction;

    public AccessTokenVerifier(
            JwtService jwtService,
            RefreshTokenRepository refreshTokenRepository,
            PlatformTransactionManager transactionManager) {
        this.jwtService = jwtService;
        this.refreshTokenRepository = refreshTokenRepository;
        this.readOnlyTransaction = new TransactionTemplate(transactionManager);
        this.readOnlyTransaction.setReadOnly(true);
        this.boundedReadOnlyTransaction = new TransactionTemplate(transactionManager);
        this.boundedReadOnlyTransaction.setReadOnly(true);
        this.boundedReadOnlyTransaction.setTimeout(ACTIVE_SESSIONS_TIMEOUT_SECONDS);
    }

    /**
     * @return los claims si el token es valido y su sesion sigue vigente;
     *         vacio ante cualquier otro caso, sin distinguir el motivo
     */
    public Optional<JwtService.TokenClaims> verify(String accessToken) {
        JwtService.TokenClaims claims;
        try {
            claims = jwtService.parse(accessToken);
        } catch (RuntimeException tokenIsNotUsable) {
            return Optional.empty();
        }

        Boolean active = TenantContext.callAs(claims.tenantId(),
                () -> readOnlyTransaction.execute(status ->
                        refreshTokenRepository.isSessionActive(claims.sessionId(), Instant.now())));

        return Boolean.TRUE.equals(active) ? Optional.of(claims) : Optional.empty();
    }

    /**
     * De las sesiones dadas, todas del mismo restaurante, devuelve las que
     * siguen vigentes. Aplica la misma regla que {@link #verify} en una sola
     * consulta y con el mismo tenant fijado, asi que RLS oculta cualquier
     * sesion de otro restaurante: se reporta como no vigente. La consulta
     * tiene un tope de {@value #ACTIVE_SESSIONS_TIMEOUT_SECONDS} s.
     */
    public Set<UUID> activeSessions(UUID tenantId, Set<UUID> sessionIds) {
        if (sessionIds.isEmpty()) {
            return Set.of();
        }
        List<UUID> active = TenantContext.callAs(tenantId,
                () -> boundedReadOnlyTransaction.execute(status ->
                        refreshTokenRepository.findActiveSessions(sessionIds, Instant.now())));
        return active == null ? Set.of() : Set.copyOf(active);
    }
}
