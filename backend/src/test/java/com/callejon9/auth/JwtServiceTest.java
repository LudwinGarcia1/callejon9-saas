package com.callejon9.auth;

import com.callejon9.auth.service.JwtService;
import com.callejon9.user.domain.User;
import com.callejon9.user.domain.UserRole;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class JwtServiceTest {

    @Autowired
    private JwtService jwtService;

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
    void tokenCarriesUserTenantAndRole() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        String token = jwtService.generateAccessToken(sampleUser(userId, tenantId));
        JwtService.TokenClaims claims = jwtService.parse(token);

        assertThat(claims.userId()).isEqualTo(userId);
        assertThat(claims.tenantId()).isEqualTo(tenantId);
        assertThat(claims.role()).isEqualTo(UserRole.ADMIN);
    }

    @Test
    void tamperedTokenIsRejected() {
        String token = jwtService.generateAccessToken(
                sampleUser(UUID.randomUUID(), UUID.randomUUID()));
        String tampered = token.substring(0, token.length() - 4) + "aaaa";

        assertThatThrownBy(() -> jwtService.parse(tampered))
                .isInstanceOf(io.jsonwebtoken.JwtException.class);
    }
}
