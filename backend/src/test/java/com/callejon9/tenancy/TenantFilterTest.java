package com.callejon9.tenancy;

import com.callejon9.support.TestSessions;
import com.callejon9.user.domain.UserRole;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TenantFilterTest {

    @Autowired
    private MockMvc mockMvc;

    private static final String SLUG_PREFIX = "filtro-test";

    @Autowired
    private TestSessions testSessions;

    @AfterEach
    void cleanUp() {
        testSessions.deleteTenants(SLUG_PREFIX);
    }

    private String tokenFor(UserRole role) {
        return testSessions.accessTokenForNewUser(SLUG_PREFIX, role);
    }

    @Test
    void requestWithoutTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/platform/plans"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void requestWithInvalidTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/platform/plans")
                        .cookie(new Cookie("access_token", "not-a-jwt")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminIsForbiddenFromPlatformEndpoints() throws Exception {
        // MockMvc solo ve la primera pasada de la cadena de filtros:
        // MockHttpServletResponse.sendError(...) anota el codigo de estado
        // pero no reproduce el redespacho de error de un Tomcat real
        // (DispatcherType.ERROR hacia "/error"), que vuelve a atravesar toda
        // la cadena de Spring Security. Por eso esta prueba no es suficiente
        // por si sola para garantizar el 403 contra el servidor real — ver
        // TenantFilterHttpTest, que si levanta un Tomcat embebido de verdad.
        mockMvc.perform(get("/api/v1/platform/plans")
                        .cookie(new Cookie("access_token", tokenFor(UserRole.ADMIN))))
                .andExpect(status().isForbidden());
    }

    @Test
    void postLoginIsPublicAndGetRequiresAuthentication() throws Exception {
        // POST llega a la validacion del controller sin cookie; GET exige sesion.
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/auth/login"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tenantContextAndSecurityContextAreClearedAfterTheRequest() throws Exception {
        // MockMvc ejecuta el filtro en el mismo hilo del test (no hay
        // despacho asincrono), asi que si el finally del TenantFilter no
        // limpiara los ThreadLocal, seguirian poblados aqui mismo. En Tomcat,
        // con hilos reciclados por el pool, ese residuo lo heredaria la
        // siguiente peticion que caiga en el mismo hilo — de otro tenant.
        mockMvc.perform(get("/api/v1/platform/plans")
                        .cookie(new Cookie("access_token", tokenFor(UserRole.ADMIN))))
                .andExpect(status().isForbidden());

        assertThat(TenantContext.currentOrNull()).isNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
