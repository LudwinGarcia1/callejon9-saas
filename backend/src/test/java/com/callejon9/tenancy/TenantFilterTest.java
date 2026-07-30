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
        mockMvc.perform(get("/api/v1/platform/plans")
                        .cookie(new Cookie("access_token", tokenFor(UserRole.ADMIN))))
                .andExpect(status().isForbidden());
    }

    @Test
    void loginEndpointIsPublic() throws Exception {
        // POST /api/v1/auth/login todavia no existe (llega en una tarea
        // posterior); por eso un GET no puede dar 405 Method Not Allowed
        // como haria una vez este mapeado, porque no hay ningun handler
        // registrado bajo esa ruta. Lo que si prueba esta asercion es que la
        // peticion no fue rechazada por el filtro de seguridad: un 401 aqui
        // significaria que "/api/v1/auth/**" dejo de ser publico. Un 404
        // demuestra que la peticion atraveso la cadena de seguridad sin
        // autenticacion y llego al DispatcherServlet, que es la garantia que
        // este test necesita verificar en esta tarea.
        mockMvc.perform(get("/api/v1/auth/login"))
                .andExpect(status().isNotFound());
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
