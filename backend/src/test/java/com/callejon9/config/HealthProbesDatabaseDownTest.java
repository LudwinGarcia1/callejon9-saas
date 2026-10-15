package com.callejon9.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proceso arrancado con la base inalcanzable (CAL-22): un puerto cerrado en
 * vez de detener la base compartida de la suite. Flyway y la validacion del
 * esquema se apagan y Hikari no exige conexion al arrancar, para que el
 * proceso llegue vivo y la falla aparezca solo cuando el probe consulta la
 * base, como cuando PostgreSQL cae con la aplicacion ya en servicio.
 */
@SpringBootTest(properties = {
        "test.database.mode=external",
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:1/base_inalcanzable",
        "spring.datasource.hikari.initialization-fail-timeout=-1",
        "spring.datasource.hikari.connection-timeout=250",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect",
        "spring.jpa.properties.hibernate.boot.allow_jdbc_metadata_access=false"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Probes con la base caida")
class HealthProbesDatabaseDownTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired private MockMvc mockMvc;

    @Test
    @DisplayName("la vida sigue en 200 UP: el proceso esta sano y no debe reiniciarse")
    void livenessStaysUp() throws Exception {
        String body = mockMvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(JSON.readTree(body)).isEqualTo(JSON.readTree("{\"status\":\"UP\"}"));
    }

    @Test
    @DisplayName("la disponibilidad cae a 503 DOWN sin exponer el error de la base")
    void readinessGoesDownWithoutLeakingTheCause() throws Exception {
        String body = mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isServiceUnavailable())
                .andReturn().getResponse().getContentAsString();

        assertThat(JSON.readTree(body)).isEqualTo(JSON.readTree("{\"status\":\"DOWN\"}"));
        assertThat(body).doesNotContain("127.0.0.1", "postgresql", "base_inalcanzable", "callejon9_app");
    }
}
