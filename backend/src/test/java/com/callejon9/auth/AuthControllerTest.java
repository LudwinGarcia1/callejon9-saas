package com.callejon9.auth;

import com.callejon9.platform.tenant.domain.Tenant;
import com.callejon9.platform.tenant.service.TenantOnboardingService;
import com.callejon9.tenancy.TenantContext;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Login")
class AuthControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private TenantOnboardingService onboardingService;
    @Autowired private JdbcTemplate jdbcTemplate;

    private Tenant tenant;

    @BeforeEach
    void seed() {
        tenant = onboardingService.onboard("Login Test", "login-test",
                "admin@login.com", "Admin", "Secreto123!", "FREE");
    }

    @AfterEach
    void cleanUp() {
        TenantContext.clear();
        jdbcTemplate.update("DELETE FROM tenants WHERE slug = 'login-test'");
    }

    @Test
    void validCredentialsReturnAnHttpOnlyCookie() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"slug":"login-test","email":"admin@login.com","password":"Secreto123!"}
                                """))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("access_token"))
                .andExpect(cookie().httpOnly("access_token", true))
                .andExpect(content().string(not(containsString("Secreto123!"))))
                .andExpect(content().string(not(containsString("access_token"))));
    }

    @Test
    void wrongPasswordIsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"slug":"login-test","email":"admin@login.com","password":"incorrecta"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("un login fallido no borra las cookies de la sesion que ya estaba abierta")
    void failedLoginDoesNotTouchSessionCookies() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .cookie(new Cookie("access_token", "sesion-previa"),
                                new Cookie("refresh_token", "sesion-previa"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"slug":"login-test","email":"admin@login.com","password":"incorrecta"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
    }

    @Test
    @DisplayName("un email valido de OTRO restaurante no sirve para entrar")
    void credentialsFromAnotherTenantAreRejected() throws Exception {
        onboardingService.onboard("Otro", "login-otro",
                "admin@otro.com", "Otro", "Secreto123!", "FREE");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"slug":"login-test","email":"admin@otro.com","password":"Secreto123!"}
                                """))
                .andExpect(status().isUnauthorized());

        jdbcTemplate.update("DELETE FROM tenants WHERE slug = 'login-otro'");
    }

    @Test
    void unknownTenantSlugIsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"slug":"no-existe","email":"admin@login.com","password":"Secreto123!"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("P01: campos vacios se rechazan con 400 y un mensaje por campo")
    void blankFieldsAreRejectedBeforeAuthenticating() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"slug":"","email":"   ","password":""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(cookie().doesNotExist("access_token"))
                .andExpect(jsonPath("$.title").value("Validacion fallida"))
                .andExpect(jsonPath("$.errors.slug").exists())
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.password").value("Ingresa tu contrasena."));
    }

    @Test
    @DisplayName("P02: datos invalidos devuelven un mensaje controlado sin repetir la entrada")
    void invalidFormatsReturnAControlledMessage() throws Exception {
        String longPassword = "x".repeat(101);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"slug":"Login Test' OR '1'='1","email":"no-es-correo","password":"%s"}
                                """.formatted(longPassword)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.slug")
                        .value("Solo minusculas, numeros y guiones, entre 3 y 80 caracteres."))
                .andExpect(jsonPath("$.errors.email").value("Ingresa un correo valido."))
                .andExpect(jsonPath("$.errors.password")
                        .value("La contrasena no puede exceder 100 caracteres."))
                .andExpect(content().string(not(containsString(longPassword))))
                .andExpect(content().string(not(containsString("OR '1'='1"))));
    }

    @Test
    @DisplayName("P02: un cuerpo malformado no expone detalles internos ni la contrasena")
    void malformedBodyDoesNotLeakInternals() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"slug\":\"login-test\",\"password\":\"Secreto123!\""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail")
                        .value("El cuerpo de la solicitud no tiene un formato valido."))
                .andExpect(content().string(not(containsString("Secreto123!"))))
                .andExpect(content().string(not(containsString("Jackson"))))
                .andExpect(content().string(not(containsString("LoginRequest"))));
    }

    @Test
    @DisplayName("un 401 no revela cual de los tres datos fallo")
    void unauthorizedResponseHasNoBody() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"slug":"login-test","email":"nadie@login.com","password":"Secreto123!"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(cookie().doesNotExist("access_token"))
                .andExpect(content().string(""));
    }
}
