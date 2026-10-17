package com.callejon9.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OpenApiConfigTest {
    @Autowired private MockMvc mockMvc;

    @Test
    void serverStaysRelativeBehindTlsTerminatingProxy() throws Exception {
        // App Service termina el TLS y reenvia por http al puerto 80.
        mockMvc.perform(get("http://app-callejon9-api.azurewebsites.net/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.servers", hasSize(1)))
                .andExpect(jsonPath("$.servers[0].url").value("/"));
    }
}
