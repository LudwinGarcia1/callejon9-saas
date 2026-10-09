package com.callejon9.config;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.handler.SimpleUrlHandlerMapping;
import org.springframework.web.servlet.resource.ResourceHttpRequestHandler;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Puerta obligatoria de mvnw verify: contrato completo con los mappings reales.
 */
@SpringBootTest
@ActiveProfiles("test")
class AuthorizationApplicationContractTest {
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
        report.add("CAL-7: contraste obligatorio con la matriz de CAL-5 y contratos de infraestructura.");
        report.add("Mappings API: " + api.size() + "; mappings totales (con infraestructura): " + endpoints.size());
        report.add("Incluye HEAD implicito y condiciones de mapping; no admite endpoints fuera de matriz.");
        report.add("Las decisiones son de filtros/anotaciones; no ejecutan validaciones manuales del controller.");
        for (var endpoint : endpoints) {
            report.add((endpoint.route().path().startsWith("/api/v1/") ? "API: " : "INFRA: ")
                    + (endpoint.route().path().startsWith("/api/v1/") ? verifier.inspect(endpoint).describe()
                            : endpoint.description() + "; decisiones de filtro=" + verifier.webDecisions(endpoint)));
        }
        Path output = Path.of("target", "authorization-contract-audit.txt");
        Files.createDirectories(output.getParent());
        Files.write(output, report, StandardCharsets.UTF_8);
        verifier.requireValid(api);
        AuthorizationContractMatrix.requireMatches(endpoints, AuthorizationContractMatrix.load(), verifier);
    }

    @Test
    void staticResourcesAreInventoriedAndSwaggerKeepsItsExactPublicScope() throws Exception {
        var resourcePatterns = context.getBeansOfType(SimpleUrlHandlerMapping.class).values().stream()
                .flatMap(mapping -> mapping.getHandlerMap().entrySet().stream())
                .filter(entry -> entry.getValue() instanceof ResourceHttpRequestHandler)
                .map(java.util.Map.Entry::getKey).collect(java.util.stream.Collectors.toSet());
        Files.writeString(Path.of("target", "authorization-resource-inventory.txt"), resourcePatterns.toString());
        assertThat(resourcePatterns).containsExactlyInAnyOrder("/webjars/**", "/**", "/swagger-ui*/**",
                "/swagger-ui*/*swagger-initializer.js");
        var verifier = new AuthorizationContractVerifier(context, springSecurityFilterChain);
        for (String method : Set.of("GET", "HEAD")) {
            for (String path : Set.of("/swagger-ui/index.html", "/swagger-ui/swagger-ui-bundle.js",
                    "/swagger-ui/swagger-initializer.js")) {
                assertThat(verifier.webDecisions(new AuthorizationContractVerifier.Route(method, path)))
                        .containsEntry("ANONYMOUS", true);
            }
            for (String path : Set.of("/webjars/contract-probe.js", "/contract-probe.js", "/swagger-ui-other/index.html")) {
                assertThat(verifier.webDecisions(new AuthorizationContractVerifier.Route(method, path)))
                        .containsEntry("ANONYMOUS", false);
            }
        }
    }

    @Test
    void nonResourceHandlersAreInventoriedAndWebSocketRequiresSession() {
        var handlers = context.getBeansOfType(SimpleUrlHandlerMapping.class).values().stream()
                .flatMap(mapping -> mapping.getHandlerMap().entrySet().stream())
                .filter(entry -> !(entry.getValue() instanceof ResourceHttpRequestHandler))
                .map(java.util.Map.Entry::getKey).collect(java.util.stream.Collectors.toSet());
        assertThat(handlers).containsExactly("/ws");
        var verifier = new AuthorizationContractVerifier(context, springSecurityFilterChain);
        assertThat(verifier.webDecisions(new AuthorizationContractVerifier.Route("GET", "/ws")))
                .containsEntry("ANONYMOUS", false)
                .containsEntry("ADMIN", true).containsEntry("WAITER", true)
                .containsEntry("CASHIER", true).containsEntry("KITCHEN", true)
                .containsEntry("SUPER_ADMIN", true);
    }
}
