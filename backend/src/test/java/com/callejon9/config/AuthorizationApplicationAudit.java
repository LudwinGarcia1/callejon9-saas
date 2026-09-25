package com.callejon9.config;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Auditoría estricta opt-in durante la dependencia CAL-5, no una puerta de CI activa.
 * Ejecutar explícitamente con -Dtest=AuthorizationApplicationAudit.
 */
@SpringBootTest
@ActiveProfiles("test")
class AuthorizationApplicationAudit {
    @Autowired private WebApplicationContext context;
    @Autowired private FilterChainProxy springSecurityFilterChain;

    @Test
    @DisplayName("Inventariar mappings reales y rechazar huecos de autorización de la API")
    void auditApplicationEndpoints() throws Exception {
        var verifier = new AuthorizationContractVerifier(context, springSecurityFilterChain);
        var endpoints = verifier.discover();
        var api = endpoints.stream().filter(e -> e.route().path().startsWith("/api/v1/")).toList();
        assertThat(api).isNotEmpty();
        assertThat(endpoints).noneMatch(e -> e.handler().getBeanType().getName()
                .startsWith("callejon9.contractfixtures."));

        var report = new ArrayList<String>();
        report.add("CAL-7: diagnóstico, no aprobación de la matriz pendiente de CAL-5.");
        report.add("Mappings API: " + api.size() + "; mappings totales (con infraestructura): " + endpoints.size());
        report.add("Infraestructura se informa por separado; no se aprueba automáticamente.");
        report.add("Las decisiones son de filtros/anotaciones; no ejecutan validaciones manuales del controller.");
        for (var endpoint : endpoints) {
            report.add((endpoint.route().path().startsWith("/api/v1/") ? "API: " : "INFRA: ")
                    + verifier.inspect(endpoint).describe());
        }
        Path output = Path.of("target", "authorization-contract-audit.txt");
        Files.createDirectories(output.getParent());
        Files.write(output, report, StandardCharsets.UTF_8);
        verifier.requireValid(api);
    }
}
