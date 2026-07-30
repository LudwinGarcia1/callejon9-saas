package com.callejon9.auth;

import com.callejon9.auth.service.AuthService;
import com.callejon9.platform.tenant.domain.Tenant;
import com.callejon9.platform.tenant.service.TenantOnboardingService;
import com.callejon9.tenancy.TenantContext;
import com.callejon9.user.domain.User;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GET /api/v1/auth/me: como el cookie del token es httpOnly, es la unica via
 * que tiene el frontend para recuperar quien esta autenticado tras refrescar
 * la pagina.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Quien soy (GET /me)")
class AuthControllerMeTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private TenantOnboardingService onboardingService;
    @Autowired private AuthService authService;
    @Autowired private JdbcTemplate jdbcTemplate;

    private Tenant tenantA;
    private User adminA;
    private String accessTokenA;

    @BeforeEach
    void seed() {
        tenantA = onboardingService.onboard("Me Test", "me-test",
                "admin@me.com", "Admin Me", "Secreto123!", "FREE");

        AuthService.AuthenticatedUser authenticated =
                authService.authenticate("me-test", "admin@me.com", "Secreto123!");
        adminA = authenticated.user();
        accessTokenA = authenticated.accessToken();
    }

    @AfterEach
    void cleanUp() {
        TenantContext.clear();
        jdbcTemplate.update("DELETE FROM tenants WHERE slug IN ('me-test','me-otro')");
    }

    @Test
    @DisplayName("con cookie valida devuelve el usuario y el tenant actuales")
    void validCookieReturnsCurrentUserAndTenant() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me").cookie(new Cookie("access_token", accessTokenA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(adminA.getId().toString()))
                .andExpect(jsonPath("$.fullName").value("Admin Me"))
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.tenantId").value(tenantA.getId().toString()))
                .andExpect(jsonPath("$.slug").value("me-test"))
                .andExpect(jsonPath("$.restaurantName").value("Me Test"));
    }

    @Test
    @DisplayName("sin cookie devuelve 401")
    void noCookieIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("un token invalido o manipulado devuelve 401")
    void invalidTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me").cookie(new Cookie("access_token", "not-a-real-jwt")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("una cookie del tenant A nunca devuelve datos del tenant B")
    void cookieFromTenantANeverReturnsTenantBData() throws Exception {
        Tenant tenantB = onboardingService.onboard("Me Otro", "me-otro",
                "admin@meotro.com", "Admin Otro", "Secreto123!", "FREE");
        AuthService.AuthenticatedUser authenticatedB =
                authService.authenticate("me-otro", "admin@meotro.com", "Secreto123!");

        mockMvc.perform(get("/api/v1/auth/me").cookie(new Cookie("access_token", accessTokenA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(adminA.getId().toString()))
                .andExpect(jsonPath("$.tenantId").value(tenantA.getId().toString()))
                .andExpect(jsonPath("$.slug").value("me-test"))
                .andExpect(jsonPath("$.restaurantName").value("Me Test"));

        mockMvc.perform(get("/api/v1/auth/me")
                        .cookie(new Cookie("access_token", authenticatedB.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(authenticatedB.user().getId().toString()))
                .andExpect(jsonPath("$.tenantId").value(tenantB.getId().toString()))
                .andExpect(jsonPath("$.slug").value("me-otro"))
                .andExpect(jsonPath("$.restaurantName").value("Me Otro"));
    }
}
