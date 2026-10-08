package com.callejon9.platform;

import com.callejon9.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * La politica de contrasenas aplicada en el alta publica de restaurante. Las
 * reglas en detalle se cubren en {@code PasswordPolicyTest}; aqui se prueba
 * que el endpoint las aplica, que el error llega en {@code errors.password} y
 * que una contrasena rechazada no deja nada creado.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Politica de contrasenas en el registro")
class SignupPasswordPolicyTest {

    private static final String SLUG = "politica-test";

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        TenantContext.clear();
        jdbcTemplate.update("DELETE FROM tenants WHERE slug = ?", SLUG);
    }

    private String signupBody(String password) {
        return """
                {"restaurantName":"Politica Test","slug":"%s","adminEmail":"gerente@politica.mx",
                 "adminFullName":"Rosa Mendez","password":"%s","planCode":"PRO"}
                """.formatted(SLUG, password);
    }

    private int tenantsWithSlug() {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM tenants WHERE slug = ?", Integer.class, SLUG);
    }

    @Test
    @DisplayName("una contrasena comun se rechaza con 400 y no crea el restaurante")
    void commonPasswordIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupBody("Password2024!")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.password")
                        .value("Esa contrasena es demasiado comun. Elige otra."))
                .andExpect(content().string(not(containsString("Password2024!"))));

        assertThat(tenantsWithSlug()).isZero();
    }

    @Test
    @DisplayName("una contrasena con el nombre del administrador se rechaza")
    void passwordWithPersonalDataIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupBody("Mendez-7781x")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.password").value(
                        "La contrasena no debe incluir tu nombre, tu correo ni el identificador del restaurante."));

        assertThat(tenantsWithSlug()).isZero();
    }

    @Test
    void shortPasswordIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupBody("Ab1-xyz")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.password")
                        .value("La contrasena debe tener al menos 10 caracteres."));
    }

    @Test
    void emptyPasswordReportsOnlyTheRequiredMessage() throws Exception {
        mockMvc.perform(post("/api/v1/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupBody("")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.password").value("Ingresa una contrasena."));
    }

    @Test
    void strongPasswordCreatesTheRestaurant() throws Exception {
        mockMvc.perform(post("/api/v1/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupBody("Mantel-Azul-47")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.slug").value(SLUG));

        assertThat(tenantsWithSlug()).isEqualTo(1);
    }
}
