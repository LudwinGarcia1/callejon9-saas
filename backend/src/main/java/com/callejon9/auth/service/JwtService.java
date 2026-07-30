package com.callejon9.auth.service;

import com.callejon9.user.domain.User;
import com.callejon9.user.domain.UserRole;
import io.jsonwebtoken.Claims;
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

    /** Claims que viajan en el token. */
    public record TokenClaims(UUID userId, UUID tenantId, UserRole role) {
    }

    private static final String CLAIM_TENANT = "tid";
    private static final String CLAIM_ROLE = "role";

    private final SecretKey signingKey;
    private final Duration accessTokenTtl;

    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.access-token-minutes}") long accessTokenMinutes) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenTtl = Duration.ofMinutes(accessTokenMinutes);
    }

    public String generateAccessToken(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(user.getId().toString())
                .claim(CLAIM_TENANT, user.getTenantId().toString())
                .claim(CLAIM_ROLE, user.getRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(accessTokenTtl)))
                .signWith(signingKey)
                .compact();
    }

    public TokenClaims parse(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        return new TokenClaims(
                UUID.fromString(claims.getSubject()),
                UUID.fromString(claims.get(CLAIM_TENANT, String.class)),
                UserRole.valueOf(claims.get(CLAIM_ROLE, String.class)));
    }
}
