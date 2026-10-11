package com.callejon9.platform;

import com.callejon9.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Freno contra altas masivas en el registro publico de restaurantes. El
 * limitador es un bean compartido por todo el contexto de pruebas, asi que
 * cada prueba usa IPs publicas propias (rango de documentacion 203.0.113.0/24)
 * y sus propios slugs. Las reglas de la ventana se prueban con reloj
 * controlado en {@code SlidingWindowRateLimiterTest}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Limite de altas de restaurantes")
class SignupRateLimitTest {

    private static final String PASSWORD = "Mantel-Azul-47";

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Value("${app.signup.rate-limit.max-per-ip}") private int maxPerIp;

    /** Prefijo de los slugs de cada prueba: la limpieza borra solo lo suyo. */
    private String slugPrefix;

    @BeforeEach
    void prefix(TestInfo testInfo) {
        slugPrefix = "alta-" + testInfo.getTestMethod().orElseThrow().getName()
                .replaceAll("([A-Z])", "-$1").toLowerCase();
    }

    @AfterEach
    void cleanUp() {
        TenantContext.clear();
        jdbcTemplate.update("DELETE FROM tenants WHERE slug LIKE ?", slugPrefix + "%");
    }

    private ResultActions signup(String remoteAddress, String slug) throws Exception {
        return mockMvc.perform(signupRequest(remoteAddress, slug));
    }

    private MockHttpServletRequestBuilder signupRequest(String remoteAddress, String slug) {
        return post("/api/v1/signup")
                .with(request -> {
                    request.setRemoteAddr(remoteAddress);
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"restaurantName":"Alta Test","slug":"%s","adminEmail":"admin@alta.mx",
                         "adminFullName":"Rosa Mendez","password":"%s","planCode":"FREE"}
                        """.formatted(slug, PASSWORD));
    }

    /** Consume el cupo completo de la IP con altas validas. */
    private void exhaust(String remoteAddress) throws Exception {
        for (int i = 0; i < maxPerIp; i++) {
            signup(remoteAddress, slugPrefix + "-" + i).andExpect(status().isCreated());
        }
    }

    private int tenantsWithSlug(String slug) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM tenants WHERE slug = ?", Integer.class, slug);
    }

    @Test
    @DisplayName("P1: un cliente sin altas recientes registra su restaurante con 201")
    void signupFromFreshClientIsAccepted() throws Exception {
        String slug = slugPrefix + "-ok";

        signup("203.0.113.11", slug)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.slug").value(slug));

        assertThat(tenantsWithSlug(slug)).isEqualTo(1);
    }

    @Test
    @DisplayName("P2: al superar el limite responde 429 con Retry-After y no crea el restaurante")
    void signupBeyondLimitIsRejected() throws Exception {
        String ip = "203.0.113.12";
        exhaust(ip);
        String blockedSlug = slugPrefix + "-extra";

        signup(ip, blockedSlug)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", matchesPattern("[1-9]\\d*")))
                .andExpect(jsonPath("$.title").value("Demasiadas altas"))
                .andExpect(content().string(not(containsString(blockedSlug))))
                .andExpect(content().string(not(containsString("admin@alta.mx"))));

        assertThat(tenantsWithSlug(blockedSlug)).isZero();
    }

    @Test
    @DisplayName("P3: otra IP no se afecta y una cabecera X-Forwarded-For falsa no evade el limite")
    void limitIsPerClientAndCannotBeSpoofed() throws Exception {
        String blockedIp = "203.0.113.13";
        exhaust(blockedIp);

        // Desde una IP publica la cabecera no se cree: sigue contando 203.0.113.13.
        String spoofedSlug = slugPrefix + "-spoof";
        mockMvc.perform(signupRequest(blockedIp, spoofedSlug)
                        .header("X-Forwarded-For", "198.51.100.7"))
                .andExpect(status().isTooManyRequests());
        assertThat(tenantsWithSlug(spoofedSlug)).isZero();

        String otherSlug = slugPrefix + "-otra";
        signup("203.0.113.14", otherSlug).andExpect(status().isCreated());
        assertThat(tenantsWithSlug(otherSlug)).isEqualTo(1);
    }

    @Test
    @DisplayName("estrenar una direccion IPv6 de la misma red /64 no evade el limite")
    void rotatingIpv6AddressesInTheSameNetworkCannotEvade() throws Exception {
        for (int i = 1; i <= maxPerIp; i++) {
            signup("2001:db8:1:2::" + i, slugPrefix + "-" + i).andExpect(status().isCreated());
        }

        String rotatedSlug = slugPrefix + "-rotada";
        signup("2001:db8:1:2::ff", rotatedSlug).andExpect(status().isTooManyRequests());
        assertThat(tenantsWithSlug(rotatedSlug)).isZero();

        String otherNetworkSlug = slugPrefix + "-otra-red";
        signup("2001:db8:1:3::1", otherNetworkSlug).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("un 400 por formato invalido no consume cupo")
    void invalidRequestsDoNotConsumeQuota() throws Exception {
        String ip = "203.0.113.15";
        for (int i = 0; i <= maxPerIp; i++) {
            signup(ip, "Slug Invalido").andExpect(status().isBadRequest());
        }

        String slug = slugPrefix + "-valida";
        signup(ip, slug).andExpect(status().isCreated());
        assertThat(tenantsWithSlug(slug)).isEqualTo(1);
    }

    @Test
    @DisplayName("un slug ocupado (409) si consume cupo: frena la enumeracion de restaurantes")
    void conflictsConsumeQuota() throws Exception {
        String ip = "203.0.113.16";
        String takenSlug = slugPrefix + "-ocupado";
        signup("203.0.113.17", takenSlug).andExpect(status().isCreated());

        for (int i = 0; i < maxPerIp; i++) {
            signup(ip, takenSlug).andExpect(status().isConflict());
        }
        signup(ip, takenSlug).andExpect(status().isTooManyRequests());
    }
}
