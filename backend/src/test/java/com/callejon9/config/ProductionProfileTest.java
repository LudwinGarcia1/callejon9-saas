package com.callejon9.config;

import com.callejon9.auth.web.AuthCookies;
import com.callejon9.platform.tenant.service.TenantOnboardingService;
import com.callejon9.tenancy.TenantContext;
import com.callejon9.tenancy.TenantFilter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.endpoint.web.PathMappedEndpoints;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * La aplicacion completa con el perfil prod sobre PostgreSQL real.
 *
 * <p>Solo corre con el contenedor de pruebas, que crea los roles con
 * contrasenas aleatorias. En modo external la base usa las contrasenas de
 * desarrollo, y prod se niega a arrancar con ellas: justo lo que debe hacer.
 * Los casos negativos, que no necesitan base, estan en
 * {@link ProductionConfigurationGuardTest}.
 */
@SpringBootTest(properties = {
        "app.jwt.secret=prueba-de-perfil-prod-con-un-secreto-de-mas-de-32-bytes",
        "app.realtime.allowed-origins=https://callejon9.example"})
@AutoConfigureMockMvc
@ActiveProfiles({"test", "prod"})
@DisabledIfSystemProperty(named = "test.database.mode", matches = "external")
@DisabledIfEnvironmentVariable(named = "TEST_DATABASE_MODE", matches = "external")
@DisplayName("Aplicacion con perfil prod")
class ProductionProfileTest {

    private static final String SLUG = "perfil-prod";

    @Autowired private MockMvc mockMvc;
    @Autowired private TenantOnboardingService onboardingService;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private PathMappedEndpoints actuatorEndpoints;

    @AfterEach
    void cleanUp() {
        TenantContext.clear();
        jdbcTemplate.update("DELETE FROM tenants WHERE slug = ?", SLUG);
    }

    @Test
    @DisplayName("arranca con credenciales validas y el login emite cookies Secure")
    void loginIssuesSecureCookies() throws Exception {
        onboardingService.onboard("Perfil prod", SLUG, "admin@prod.mx", "Admin", "Secreto123!", "FREE");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"slug":"perfil-prod","email":"admin@prod.mx","password":"Secreto123!"}
                                """))
                .andExpect(status().isOk())
                .andExpect(cookie().secure(TenantFilter.ACCESS_TOKEN_COOKIE, true))
                .andExpect(cookie().httpOnly(TenantFilter.ACCESS_TOKEN_COOKIE, true))
                .andExpect(cookie().secure(AuthCookies.REFRESH_TOKEN_COOKIE, true))
                .andExpect(cookie().httpOnly(AuthCookies.REFRESH_TOKEN_COOKIE, true));
    }

    @Test
    @DisplayName("actuator publica solo health, sin detalles")
    void actuatorExposesOnlyHealth() throws Exception {
        assertThat(actuatorEndpoints.getAllPaths()).containsExactly("/actuator/health");

        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist())
                .andExpect(jsonPath("$.details").doesNotExist());
    }

    @Test
    @DisplayName("OpenAPI y Swagger UI no se sirven")
    void openApiIsNotServed() throws Exception {
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isNotFound());
        mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isNotFound());
    }
}
