package com.callejon9.tenancy;

import com.callejon9.auth.service.JwtService;
import com.callejon9.user.domain.User;
import com.callejon9.user.domain.UserRole;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TenantFilterTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    private String tokenFor(UserRole role) {
        User user = User.builder()
                .email("demo@demo.com").passwordHash("x").fullName("Demo")
                .role(role).active(true).build();
        user.setId(UUID.randomUUID());
        user.setTenantId(UUID.randomUUID());
        return jwtService.generateAccessToken(user);
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
    void loginEndpointIsPublic() throws Exception {
        // Un GET contra una ruta mapeada solo a POST da 405 Method Not
        // Allowed. Eso prueba que la peticion atraveso la cadena de seguridad
        // sin autenticacion y llego al DispatcherServlet: un 401 aqui
        // significaria que "/api/v1/auth/**" dejo de ser publico.
        mockMvc.perform(get("/api/v1/auth/login"))
                .andExpect(status().isMethodNotAllowed());
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
