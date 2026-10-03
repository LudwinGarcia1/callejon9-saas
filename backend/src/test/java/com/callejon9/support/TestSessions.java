package com.callejon9.support;

import com.callejon9.auth.service.RefreshTokenService;
import com.callejon9.tenancy.TenantContext;
import com.callejon9.user.domain.User;
import com.callejon9.user.domain.UserRole;
import com.callejon9.user.repository.UserRepository;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Emite access tokens con una sesion real en refresh_tokens. Desde que el
 * servidor verifica en cada peticion que la sesion ({@code sid}) siga
 * vigente, firmar un JWT a mano ya no autentica: las pruebas necesitan la
 * misma fila que crearia un login.
 */
@Component
public class TestSessions {

    private final RefreshTokenService refreshTokenService;
    private final UserRepository userRepository;
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;

    public TestSessions(
            RefreshTokenService refreshTokenService,
            UserRepository userRepository,
            JdbcTemplate jdbcTemplate,
            TransactionTemplate transactionTemplate) {
        this.refreshTokenService = refreshTokenService;
        this.userRepository = userRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = transactionTemplate;
    }

    /** Abre una sesion para un usuario ya persistido y devuelve su access token. */
    public String accessTokenFor(User user) {
        return TenantContext.callAs(user.getTenantId(),
                () -> refreshTokenService.issueForLogin(user).accessToken());
    }

    /**
     * Crea un restaurante y un usuario reales con el rol indicado y abre una
     * sesion. El slug lleva {@code slugPrefix} para que la prueba pueda
     * borrar en cascada todo lo que creo.
     */
    public String accessTokenForNewUser(String slugPrefix, UserRole role) {
        User user = newUser(slugPrefix, role);
        return accessTokenFor(user);
    }

    public User newUser(String slugPrefix, UserRole role) {
        String slug = slugPrefix + "-" + UUID.randomUUID().toString().substring(0, 8);
        // tenants es control plane: sin RLS, se escribe sin contexto.
        UUID tenantId = jdbcTemplate.queryForObject(
                "INSERT INTO tenants (name, slug) VALUES (?, ?) RETURNING id", UUID.class, slug, slug);

        return newUserIn(tenantId, role);
    }

    /**
     * Persiste un usuario con ese rol en un restaurante que ya existe. El
     * correo es unico para no chocar con el administrador del onboarding.
     */
    public User newUserIn(UUID tenantId, UserRole role) {
        String email = role.name().toLowerCase() + "-"
                + UUID.randomUUID().toString().substring(0, 8) + "@prueba.test";
        return TenantContext.callAs(tenantId, () -> transactionTemplate.execute(status ->
                userRepository.save(User.builder()
                        .email(email)
                        .passwordHash("x")
                        .fullName("Prueba " + role.name())
                        .role(role)
                        .active(true)
                        .build())));
    }

    /** Borra los restaurantes creados con {@code slugPrefix} y todo lo que cuelga de ellos. */
    public void deleteTenants(String slugPrefix) {
        jdbcTemplate.update("DELETE FROM tenants WHERE slug LIKE ?", slugPrefix + "-%");
    }
}
