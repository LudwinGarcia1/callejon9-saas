package com.callejon9.auth;

import com.callejon9.auth.service.JwtService;
import com.callejon9.platform.tenant.domain.Tenant;
import com.callejon9.platform.tenant.service.TenantOnboardingService;
import com.callejon9.realtime.JwtHandshakeInterceptor;
import com.callejon9.tenancy.TenantContext;
import com.callejon9.user.domain.User;
import com.callejon9.user.domain.UserRole;
import jakarta.servlet.http.Cookie;
import java.util.HashMap;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * El logout invalida el access token en el servidor, no solo en el navegador.
 * Cada prueba usa un JWT que todavia no expira: si la API lo acepta despues
 * de revocar la sesion, el logout seria solo cosmetico.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Revocacion de la sesion en el servidor")
class SessionRevocationTest {

    private static final String ACCESS = "access_token";
    private static final String REFRESH = "refresh_token";

    @Autowired private MockMvc mockMvc;
    @Autowired private TenantOnboardingService onboardingService;
    @Autowired private JwtService jwtService;
    @Autowired private JwtHandshakeInterceptor handshakeInterceptor;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private TransactionTemplate transactionTemplate;

    private Tenant tenant;

    @BeforeEach
    void seed() {
        tenant = onboardingService.onboard("Revocacion Test", "revocacion-test",
                "admin@revocacion.com", "Admin", "Secreto123!", "FREE");
    }

    @AfterEach
    void cleanUp() {
        TenantContext.clear();
        jdbcTemplate.update("DELETE FROM tenants WHERE slug IN ('revocacion-test','revocacion-otro')");
    }

    @Test
    @DisplayName("tras el logout, el mismo access token (aun sin expirar) responde 401")
    void logoutInvalidatesTheAccessTokenOnTheServer() throws Exception {
        Session session = login("revocacion-test", "admin@revocacion.com");
        expectMe(session.access(), 200);

        logout(session);

        expectMe(session.access(), 401);
    }

    @Test
    @DisplayName("un logout que solo envia access_token (Swagger, app movil) tambien revoca la sesion")
    void logoutWithOnlyTheAccessCookieRevokesTheSession() throws Exception {
        Session session = login("revocacion-test", "admin@revocacion.com");

        mockMvc.perform(post("/api/v1/auth/logout").cookie(new Cookie(ACCESS, session.access())))
                .andExpect(status().isNoContent());

        expectMe(session.access(), 401);
        mockMvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie(REFRESH, session.refresh())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("un segundo logout con el access token ya revocado responde 401: no queda sesion que cerrar")
    void logoutWithARevokedAccessTokenIsUnauthorized() throws Exception {
        Session session = login("revocacion-test", "admin@revocacion.com");
        logout(session);

        // El logout exige sesion vigente (CAL-5); la revocada ya no autentica.
        mockMvc.perform(post("/api/v1/auth/logout").cookie(new Cookie(ACCESS, session.access())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("el logout tambien invalida access tokens anteriores de la misma sesion")
    void logoutInvalidatesEarlierAccessTokensOfTheSession() throws Exception {
        Session first = login("revocacion-test", "admin@revocacion.com");
        Session rotated = refresh(first.refresh());

        // Rotar no corta la sesion: el access token previo sigue sirviendo.
        expectMe(first.access(), 200);
        expectMe(rotated.access(), 200);

        logout(rotated);

        expectMe(first.access(), 401);
        expectMe(rotated.access(), 401);
    }

    @Test
    @DisplayName("si se detecta reutilizacion del refresh, los access tokens de la sesion dejan de servir")
    void replayDetectionCutsTheAccessTokensToo() throws Exception {
        Session first = login("revocacion-test", "admin@revocacion.com");
        Session rotated = refresh(first.refresh());

        mockMvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie(REFRESH, first.refresh())))
                .andExpect(status().isUnauthorized());

        expectMe(rotated.access(), 401);
    }

    @Test
    @DisplayName("cerrar una sesion no cierra las demas del mismo usuario")
    void logoutOnlyRevokesItsOwnSession() throws Exception {
        Session phone = login("revocacion-test", "admin@revocacion.com");
        Session tablet = login("revocacion-test", "admin@revocacion.com");

        logout(phone);

        expectMe(phone.access(), 401);
        expectMe(tablet.access(), 200);
    }

    @Test
    @DisplayName("una sesion vencida deja de autorizar aunque el JWT no haya expirado")
    void expiredSessionStopsAuthorizing() throws Exception {
        Session session = login("revocacion-test", "admin@revocacion.com");

        TenantContext.set(tenant.getId());
        try {
            transactionTemplate.executeWithoutResult(status -> jdbcTemplate.update(
                    "UPDATE refresh_tokens SET expires_at = now() - interval '1 second'"));
        } finally {
            TenantContext.clear();
        }

        expectMe(session.access(), 401);
    }

    @Test
    @DisplayName("un sid inventado o de otro restaurante no autentica: RLS oculta la sesion ajena")
    void foreignOrUnknownSessionIdIsRejected() throws Exception {
        onboardingService.onboard("Revocacion Otro", "revocacion-otro",
                "admin@revocacion-otro.com", "Otro", "Secreto123!", "FREE");
        Session own = login("revocacion-test", "admin@revocacion.com");
        Session foreign = login("revocacion-otro", "admin@revocacion-otro.com");

        JwtService.TokenClaims ownClaims = jwtService.parse(own.access());
        UUID foreignSession = jwtService.parse(foreign.access()).sessionId();
        User self = sampleUser(ownClaims);

        // Firma valida, identidad propia, pero la sesion es del otro tenant.
        String withForeignSession = jwtService.generateAccessToken(self, foreignSession);
        expectMe(withForeignSession, 401);

        String withUnknownSession = jwtService.generateAccessToken(self, UUID.randomUUID());
        expectMe(withUnknownSession, 401);
    }

    @Test
    @DisplayName("el handshake de WebSocket rechaza el access token de una sesion cerrada")
    void websocketHandshakeRejectsARevokedSession() throws Exception {
        Session session = login("revocacion-test", "admin@revocacion.com");
        assertThat(handshake(session.access())).isTrue();

        logout(session);

        assertThat(handshake(session.access())).isFalse();
    }

    // ---------------------------------------------------------------------

    private record Session(String access, String refresh) {
    }

    private Session login(String slug, String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"slug":"%s","email":"%s","password":"Secreto123!"}
                                """.formatted(slug, email)))
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

    private void logout(Session session) throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout")
                        .cookie(new Cookie(ACCESS, session.access()),
                                new Cookie(REFRESH, session.refresh())))
                .andExpect(status().isNoContent());
    }

    private void expectMe(String accessToken, int expectedStatus) throws Exception {
        mockMvc.perform(get("/api/v1/auth/me").cookie(new Cookie(ACCESS, accessToken)))
                .andExpect(status().is(expectedStatus));
    }

    private boolean handshake(String accessToken) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/ws");
        request.setCookies(new Cookie(ACCESS, accessToken));
        return handshakeInterceptor.beforeHandshake(
                new ServletServerHttpRequest(request),
                new ServletServerHttpResponse(new MockHttpServletResponse()),
                null,
                new HashMap<>());
    }

    private static Session sessionFrom(MvcResult result) {
        return new Session(
                result.getResponse().getCookie(ACCESS).getValue(),
                result.getResponse().getCookie(REFRESH).getValue());
    }

    private static User sampleUser(JwtService.TokenClaims claims) {
        User user = User.builder()
                .email("x@x.com").passwordHash("x").fullName("X")
                .role(UserRole.ADMIN).active(true).build();
        user.setId(claims.userId());
        user.setTenantId(claims.tenantId());
        return user;
    }
}
