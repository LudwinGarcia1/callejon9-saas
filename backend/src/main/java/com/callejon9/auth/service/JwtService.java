package com.callejon9.auth.service;

import com.callejon9.user.domain.User;
import com.callejon9.user.domain.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    /** Claims que viajan en el token de acceso. */
    public record TokenClaims(UUID userId, UUID tenantId, UserRole role) {
    }

    /**
     * Claims del refresh token. {@code secret} es el valor aleatorio cuyo hash
     * se guarda en refresh_tokens; el tenant viaja firmado para poder fijar el
     * contexto de RLS antes de abrir la transaccion, sin confiar en el cliente.
     */
    public record RefreshClaims(UUID userId, UUID tenantId, String secret) {
    }

    private static final String CLAIM_TENANT = "tid";
    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_TYPE = "typ";
    private static final String TYPE_ACCESS = "access";
    private static final String TYPE_REFRESH = "refresh";

    private final SecretKey signingKey;
    private final Duration accessTokenTtl;
    private final Duration refreshTokenTtl;

    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.access-token-minutes}") long accessTokenMinutes,
            @Value("${app.jwt.refresh-token-days}") long refreshTokenDays) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenTtl = Duration.ofMinutes(accessTokenMinutes);
        this.refreshTokenTtl = Duration.ofDays(refreshTokenDays);
    }

    public Duration refreshTokenTtl() {
        return refreshTokenTtl;
    }

    public String generateAccessToken(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(user.getId().toString())
                .claim(CLAIM_TENANT, user.getTenantId().toString())
                .claim(CLAIM_ROLE, user.getRole().name())
                .claim(CLAIM_TYPE, TYPE_ACCESS)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(accessTokenTtl)))
                .signWith(signingKey)
                .compact();
    }

    /**
     * Rechaza un refresh token presentado como token de acceso: comparten
     * firma, y sin esta comprobacion la cookie de refresco (que vive dias)
     * serviria para llamar a la API directamente. Los tokens emitidos antes
     * de existir el claim {@code typ} se siguen aceptando mientras no expiren.
     */
    public TokenClaims parse(String token) {
        Claims claims = parseClaims(token);
        if (TYPE_REFRESH.equals(claims.get(CLAIM_TYPE, String.class))) {
            throw new JwtException("Un refresh token no autoriza peticiones.");
        }

        return new TokenClaims(
                UUID.fromString(claims.getSubject()),
                UUID.fromString(claims.get(CLAIM_TENANT, String.class)),
                UserRole.valueOf(claims.get(CLAIM_ROLE, String.class)));
    }

    public String generateRefreshToken(UUID userId, UUID tenantId, String secret, Instant expiresAt) {
        return Jwts.builder()
                .subject(userId.toString())
                .id(secret)
                .claim(CLAIM_TENANT, tenantId.toString())
                .claim(CLAIM_TYPE, TYPE_REFRESH)
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(expiresAt))
                .signWith(signingKey)
                .compact();
    }

    public RefreshClaims parseRefreshToken(String token) {
        Claims claims = parseClaims(token);
        if (!TYPE_REFRESH.equals(claims.get(CLAIM_TYPE, String.class)) || claims.getId() == null) {
            throw new JwtException("El token no es un refresh token.");
        }

        return new RefreshClaims(
                UUID.fromString(claims.getSubject()),
                UUID.fromString(claims.get(CLAIM_TENANT, String.class)),
                claims.getId());
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
