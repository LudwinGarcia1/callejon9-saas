package com.callejon9.auth;

import com.callejon9.auth.service.JwtService;
import com.callejon9.user.domain.User;
import com.callejon9.user.domain.UserRole;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class JwtServiceTest {

    @Autowired
    private JwtService jwtService;

    @Value("${app.jwt.secret}")
    private String secret;

    private User sampleUser(UUID userId, UUID tenantId) {
        User user = User.builder()
                .email("demo@demo.com")
                .passwordHash("x")
                .fullName("Demo")
                .role(UserRole.ADMIN)
                .active(true)
                .build();
        user.setId(userId);
        user.setTenantId(tenantId);
        return user;
    }

    @Test
    void tokenCarriesUserTenantRoleAndSession() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();

        String token = jwtService.generateAccessToken(sampleUser(userId, tenantId), sessionId);
        JwtService.TokenClaims claims = jwtService.parse(token);

        assertThat(claims.userId()).isEqualTo(userId);
        assertThat(claims.tenantId()).isEqualTo(tenantId);
        assertThat(claims.role()).isEqualTo(UserRole.ADMIN);
        assertThat(claims.sessionId()).isEqualTo(sessionId);
    }

    @Test
    @DisplayName("un access token sin sesion (sid) se rechaza: no habria forma de revocarlo")
    void accessTokenWithoutSessionIsRejected() {
        String withoutSession = Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .claim("tid", UUID.randomUUID().toString())
                .claim("role", UserRole.ADMIN.name())
                .claim("typ", "access")
                .expiration(Date.from(Instant.now().plusSeconds(60)))
                .signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)))
                .compact();

        assertThatThrownBy(() -> jwtService.parse(withoutSession))
                .isInstanceOf(io.jsonwebtoken.JwtException.class);
    }

    @Test
    void tamperedTokenIsRejected() {
        String token = jwtService.generateAccessToken(
                sampleUser(UUID.randomUUID(), UUID.randomUUID()), UUID.randomUUID());
        String tampered = token.substring(0, token.length() - 4) + "aaaa";

        assertThatThrownBy(() -> jwtService.parse(tampered))
                .isInstanceOf(io.jsonwebtoken.JwtException.class);
    }

    @Test
    void refreshTokenCarriesUserTenantAndSecret() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        String token = jwtService.generateRefreshToken(
                userId, tenantId, "secreto", Instant.now().plusSeconds(60));
        JwtService.RefreshClaims claims = jwtService.parseRefreshToken(token);

        assertThat(claims.userId()).isEqualTo(userId);
        assertThat(claims.tenantId()).isEqualTo(tenantId);
        assertThat(claims.secret()).isEqualTo("secreto");
    }

    @Test
    void refreshTokenIsNotAcceptedAsAccessToken() {
        String refresh = jwtService.generateRefreshToken(
                UUID.randomUUID(), UUID.randomUUID(), "secreto", Instant.now().plusSeconds(60));

        assertThatThrownBy(() -> jwtService.parse(refresh))
                .isInstanceOf(io.jsonwebtoken.JwtException.class);
    }

    @Test
    void accessTokenIsNotAcceptedAsRefreshToken() {
        String access = jwtService.generateAccessToken(
                sampleUser(UUID.randomUUID(), UUID.randomUUID()), UUID.randomUUID());

        assertThatThrownBy(() -> jwtService.parseRefreshToken(access))
                .isInstanceOf(io.jsonwebtoken.JwtException.class);
    }
}
