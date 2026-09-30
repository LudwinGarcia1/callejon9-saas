package com.callejon9.auth;

import com.callejon9.auth.service.JwtService;
import com.callejon9.platform.tenant.domain.Tenant;
import com.callejon9.platform.tenant.service.TenantOnboardingService;
import com.callejon9.tenancy.TenantContext;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Ciclo completo login → refresh → replay → logout contra PostgreSQL real.
 * Las filas de refresh_tokens se leen como callejon9_app con el tenant fijado,
 * igual que las ve la aplicacion.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Rotacion de refresh tokens")
class RefreshTokenRotationTest {

    private static final String ACCESS = "access_token";
    private static final String REFRESH = "refresh_token";

    @Autowired private MockMvc mockMvc;
    @Autowired private TenantOnboardingService onboardingService;
    @Autowired private JwtService jwtService;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private TransactionTemplate transactionTemplate;

    private Tenant tenantA;

    @BeforeEach
    void seed() {
        tenantA = onboardingService.onboard("Refresh Test", "refresh-test",
                "admin@refresh.com", "Admin", "Secreto123!", "FREE");
    }

    @AfterEach
    void cleanUp() {
        TenantContext.clear();
        jdbcTemplate.update("DELETE FROM tenants WHERE slug IN ('refresh-test','refresh-otro')");
    }

    @Test
    @DisplayName("login emite access y refresh httpOnly, Secure, SameSite=Strict y el refresh con path minimo")
    void loginIssuesBothCookiesWithSafeAttributes() throws Exception {
        mockMvc.perform(loginRequest("refresh-test", "admin@refresh.com"))
                .andExpect(status().isOk())
                .andExpect(cookie().httpOnly(ACCESS, true))
                .andExpect(cookie().secure(ACCESS, true))
                .andExpect(cookie().sameSite(ACCESS, "Strict"))
                .andExpect(cookie().path(ACCESS, "/"))
                .andExpect(cookie().httpOnly(REFRESH, true))
                .andExpect(cookie().secure(REFRESH, true))
                .andExpect(cookie().sameSite(REFRESH, "Strict"))
                .andExpect(cookie().path(REFRESH, "/api/v1/auth"));
    }

    @Test
    @DisplayName("la base guarda solo el hash del secreto, nunca el token en claro")
    void onlyTheHashIsPersisted() throws Exception {
        String refreshToken = login("refresh-test", "admin@refresh.com").refresh();
        String secret = jwtService.parseRefreshToken(refreshToken).secret();

        List<Map<String, Object>> rows = tokensOf(tenantA.getId());
        assertThat(rows).hasSize(1);
        String storedHash = (String) rows.get(0).get("token_hash");
        assertThat(storedHash)
                .isEqualTo(sha256(secret))
                .isNotEqualTo(secret)
                .doesNotContain(refreshToken);
    }

    @Test
    @DisplayName("un refresh valido queda revocado y se emite un par nuevo que funciona")
    void validRefreshRotatesThePair() throws Exception {
        Session first = login("refresh-test", "admin@refresh.com");

        Session second = refresh(first.refresh());

        assertThat(second.refresh()).isNotEqualTo(first.refresh());
        assertThat(second.access()).isNotBlank();

        List<Map<String, Object>> rows = tokensOf(tenantA.getId());
        assertThat(rows).hasSize(2);
        Map<String, Object> consumed = rows.get(0);
        Map<String, Object> issued = rows.get(1);
        assertThat(consumed.get("revoked_at")).isNotNull();
        assertThat(consumed.get("replaced_by")).isEqualTo(issued.get("id"));
        assertThat(issued.get("revoked_at")).isNull();
        assertThat(issued.get("family_id")).isEqualTo(consumed.get("family_id"));
        assertThat(issued.get("expires_at"))
                .as("rotar no alarga la sesion")
                .isEqualTo(consumed.get("expires_at"));

        mockMvc.perform(get("/api/v1/auth/me").cookie(new Cookie(ACCESS, second.access())))
                .andExpect(status().isOk());
        refresh(second.refresh());
    }

    @Test
    @DisplayName("reutilizar un refresh ya usado revoca toda la familia y responde 401 sin detalle")
    void replayRevokesTheWholeFamily() throws Exception {
        Session first = login("refresh-test", "admin@refresh.com");
        Session second = refresh(first.refresh());

        mockMvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie(REFRESH, first.refresh())))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string(""))
                .andExpect(cookie().maxAge(ACCESS, 0))
                .andExpect(cookie().maxAge(REFRESH, 0));

        assertThat(tokensOf(tenantA.getId()))
                .allSatisfy(row -> assertThat(row.get("revoked_at")).isNotNull());

        // El token legitimo mas reciente tambien quedo inutilizado.
        mockMvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie(REFRESH, second.refresh())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("logout revoca la sesion: el refresh deja de servir y se borran las cookies")
    void logoutRevokesTheSession() throws Exception {
        Session session = login("refresh-test", "admin@refresh.com");

        mockMvc.perform(post("/api/v1/auth/logout").cookie(new Cookie(REFRESH, session.refresh())))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge(ACCESS, 0))
                .andExpect(cookie().maxAge(REFRESH, 0))
                .andExpect(cookie().path(REFRESH, "/api/v1/auth"));

        assertThat(tokensOf(tenantA.getId()))
                .allSatisfy(row -> assertThat(row.get("revoked_at")).isNotNull());
        mockMvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie(REFRESH, session.refresh())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("sin cookie, con un token basura o expirado, refresh responde 401")
    void missingOrInvalidRefreshIsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie(REFRESH, "no-es-un-jwt")))
                .andExpect(status().isUnauthorized());

        Session session = login("refresh-test", "admin@refresh.com");
        var claims = jwtService.parseRefreshToken(session.refresh());
        String expired = jwtService.generateRefreshToken(claims.userId(), claims.tenantId(),
                claims.secret(), Instant.now().minusSeconds(1));
        mockMvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie(REFRESH, expired)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("un usuario desactivado ya no renueva y su sesion queda revocada")
    void inactiveUserCannotRefresh() throws Exception {
        Session session = login("refresh-test", "admin@refresh.com");

        TenantContext.set(tenantA.getId());
        try {
            transactionTemplate.executeWithoutResult(status -> jdbcTemplate.update(
                    "UPDATE users SET active = false WHERE email = 'admin@refresh.com'"));
        } finally {
            TenantContext.clear();
        }

        mockMvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie(REFRESH, session.refresh())))
                .andExpect(status().isUnauthorized())
                .andExpect(cookie().maxAge(REFRESH, 0));
        assertThat(tokensOf(tenantA.getId()))
                .allSatisfy(row -> assertThat(row.get("revoked_at")).isNotNull());
    }

    @Test
    @DisplayName("un restaurante desactivado ya no renueva: la regla del login tambien aplica al refresh")
    void inactiveTenantCannotRefresh() throws Exception {
        Session session = login("refresh-test", "admin@refresh.com");

        // tenants es control plane: sin RLS, se actualiza sin contexto.
        jdbcTemplate.update("UPDATE tenants SET active = false WHERE slug = 'refresh-test'");

        mockMvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie(REFRESH, session.refresh())))
                .andExpect(status().isUnauthorized())
                .andExpect(cookie().maxAge(REFRESH, 0));
        assertThat(tokensOf(tenantA.getId()))
                .allSatisfy(row -> assertThat(row.get("revoked_at")).isNotNull());
    }

    @Test
    @DisplayName("los tokens no son intercambiables: ni el refresh autoriza la API ni el access renueva")
    void tokensAreNotInterchangeable() throws Exception {
        Session session = login("refresh-test", "admin@refresh.com");

        mockMvc.perform(get("/api/v1/auth/me").cookie(new Cookie(ACCESS, session.refresh())))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie(REFRESH, session.access())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("un token del tenant A procesado con contexto B no lee ni modifica la sesion")
    void refreshFromTenantAIsInvisibleUnderTenantB() throws Exception {
        Tenant tenantB = onboardingService.onboard("Refresh Otro", "refresh-otro",
                "admin@refresh-otro.com", "Otro", "Secreto123!", "FREE");
        Session sessionA = login("refresh-test", "admin@refresh.com");
        Session sessionB = login("refresh-otro", "admin@refresh-otro.com");
        var claimsA = jwtService.parseRefreshToken(sessionA.refresh());

        // Mismo secreto y usuario de A, pero firmado con el tenant B: la firma
        // es valida, asi que solo RLS puede impedir que se lea la fila de A.
        String crossTenant = jwtService.generateRefreshToken(claimsA.userId(), tenantB.getId(),
                claimsA.secret(), Instant.now().plusSeconds(3600));
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new Cookie(REFRESH, crossTenant))
                        .cookie(new Cookie(ACCESS, sessionB.access())))
                .andExpect(status().isUnauthorized());

        // Un logout con el mismo token cruzado tampoco revoca nada en A.
        mockMvc.perform(post("/api/v1/auth/logout").cookie(new Cookie(REFRESH, crossTenant)))
                .andExpect(status().isNoContent());

        assertThat(tokensOf(tenantA.getId()))
                .singleElement()
                .satisfies(row -> assertThat(row.get("revoked_at")).isNull());

        // Sin contexto de tenant, RLS no revela ninguna fila.
        Integer visibleWithoutTenant = transactionTemplate.execute(status ->
                jdbcTemplate.queryForObject("SELECT count(*) FROM refresh_tokens", Integer.class));
        assertThat(visibleWithoutTenant).isZero();

        // La sesion de A sigue intacta y renovable.
        refresh(sessionA.refresh());
    }

    // ---------------------------------------------------------------------

    private record Session(String access, String refresh) {
    }

    private Session login(String slug, String email) throws Exception {
        MvcResult result = mockMvc.perform(loginRequest(slug, email))
                .andExpect(status().isOk())
                .andReturn();
        return sessionFrom(result);
    }

    private Session refresh(String refreshToken) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new Cookie(REFRESH, refreshToken)))
                .andExpect(status().isNoContent())
                .andReturn();
        return sessionFrom(result);
    }

    private static Session sessionFrom(MvcResult result) {
        return new Session(
                result.getResponse().getCookie(ACCESS).getValue(),
                result.getResponse().getCookie(REFRESH).getValue());
    }

    private static org.springframework.test.web.servlet.RequestBuilder loginRequest(String slug, String email) {
        return post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"slug":"%s","email":"%s","password":"Secreto123!"}
                        """.formatted(slug, email));
    }

    private List<Map<String, Object>> tokensOf(UUID tenantId) {
        TenantContext.set(tenantId);
        try {
            return transactionTemplate.execute(status -> jdbcTemplate.queryForList("""
                    SELECT id, token_hash, family_id, expires_at, revoked_at, replaced_by
                      FROM refresh_tokens
                     ORDER BY created_at
                    """));
        } finally {
            TenantContext.clear();
        }
    }

    private static String sha256(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
    }
}
