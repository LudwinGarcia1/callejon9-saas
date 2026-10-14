package com.callejon9.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.HealthEndpointGroups;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Probes del orquestador con la base disponible (CAL-22). El caso de la base
 * caida vive en {@link HealthProbesDatabaseDownTest}, con un contexto propio,
 * para no detener la base compartida de la suite.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Probes de vida y disponibilidad")
class HealthProbesTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired private MockMvc mockMvc;
    @Autowired private HealthEndpointGroups groups;

    @ParameterizedTest
    @ValueSource(strings = {"/actuator/health/liveness", "/actuator/health/readiness"})
    @DisplayName("sin sesion, cada probe responde 200 y solo su estado")
    void probesAnswerOnlyTheirStatusWithoutSession(String path) throws Exception {
        String body = mockMvc.perform(get(path))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // Igualdad exacta: ni componentes, ni detalles, ni datos de la base.
        assertThat(JSON.readTree(body)).isEqualTo(JSON.readTree("{\"status\":\"UP\"}"));
    }

    @Test
    @DisplayName("la vida no depende de PostgreSQL y la disponibilidad si")
    void onlyReadinessDependsOnTheDatabase() {
        var liveness = groups.get("liveness");
        var readiness = groups.get("readiness");

        assertThat(liveness.isMember("livenessState")).isTrue();
        assertThat(liveness.isMember("db")).as("liveness no debe incluir la base").isFalse();
        assertThat(liveness.isMember("readinessState")).isFalse();

        assertThat(readiness.isMember("readinessState")).isTrue();
        assertThat(readiness.isMember("db")).isTrue();
        assertThat(readiness.isMember("livenessState")).isFalse();
    }
}
