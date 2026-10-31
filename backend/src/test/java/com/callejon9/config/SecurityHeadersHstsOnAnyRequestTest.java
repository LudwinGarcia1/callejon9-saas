package com.callejon9.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Detras de App Service, TLS termina en el proxy y la API recibe cada peticion
 * como HTTP. Con app.security.hsts-on-any-request, como en prod, HSTS sale
 * igual (CAL-183).
 */
@SpringBootTest(properties = "app.security.hsts-on-any-request=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityHeadersHstsOnAnyRequestTest {
    @Autowired private MockMvc mockMvc;

    @Test
    void plainHttpPublicResponsesReceiveHsts() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(header().string("Strict-Transport-Security", "max-age=31536000"));
    }

    @Test
    void plainHttpUnauthorizedResponsesReceiveHsts() throws Exception {
        mockMvc.perform(get("/api/v1/orders"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Strict-Transport-Security", "max-age=31536000"));
    }
}
