package com.callejon9.config;

import callejon9.contractfixtures.AuthorizationFixtures;
import com.callejon9.auth.service.JwtService;
import com.callejon9.user.domain.User;
import com.callejon9.user.domain.UserRole;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authorization.AuthenticatedAuthorizationManager;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.web.DefaultSecurityFilterChain;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.util.matcher.AnyRequestMatcher;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({AuthorizationFixtures.MethodRules.class, AuthorizationFixtures.ClassRules.class,
        AuthorizationFixtures.MatcherRules.class})
@DisplayName("CAL-7: verificador de autorización con mappings y filtros reales")
class AuthorizationContractTest {
    @Autowired private WebApplicationContext context;
    @Autowired private FilterChainProxy springSecurityFilterChain;
    @Autowired private MockMvc mockMvc;
    @Autowired private JwtService jwtService;

    private AuthorizationContractVerifier verifier;
    private List<AuthorizationContractVerifier.Endpoint> endpoints;

    @BeforeEach
    void discoverMappings() {
        verifier = new AuthorizationContractVerifier(context, springSecurityFilterChain);
        endpoints = verifier.discover();
    }

    @Test
    @DisplayName("Una ruta nueva sin regla falla con controller, método HTTP y ruta")
    void rejectsUnprotectedEndpointWithActionableMessage() {
        var endpoint = endpoint("GET", "/api/v1/contract-fixtures/unprotected");
        assertThatThrownBy(() -> verifier.requireValid(List.of(endpoint)))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("MethodRules#unprotected")
                .hasMessageContaining("GET /api/v1/contract-fixtures/unprotected")
                .hasMessageContaining("authenticated() general");
    }

    @ParameterizedTest(name = "{0} permite únicamente {1}")
    @CsvSource({
            "/api/v1/contract-fixtures/method, ADMIN",
            "/api/v1/contract-fixtures/composed, ADMIN",
            "/api/v1/contract-fixtures/inherited, CASHIER",
            "/api/v1/contract-class, KITCHEN",
            "/api/v1/contract-class/override, ADMIN",
            "/api/v1/platform/contract-fixture, SUPER_ADMIN"
    })
    void recognizesEffectivePolicies(String path, String role) {
        var finding = verifier.inspect(endpoint("GET", path));
        assertThat(finding.valid()).as(finding.describe()).isTrue();
        assertThat(finding.allowed()).containsExactly(role);
    }

    @ParameterizedTest
    @CsvSource({"/api/v1/auth/contract-fixture", "/api/v1/contract-fixtures/public"})
    @DisplayName("Una ruta pública no documentada no se acepta ni bajo /auth/**")
    void rejectsUndocumentedPublicRoutes(String path) {
        assertThatThrownBy(() -> verifier.requireValid(List.of(endpoint("GET", path))))
                .isInstanceOf(AssertionError.class).hasMessageContaining("lista pública");
    }

    @Test
    @DisplayName("La lista pública distingue método HTTP y ruta exacta")
    void acceptsOnlyDocumentedPublicMethods() {
        verifier.requireValid(List.of(endpoint("POST", "/api/v1/auth/login"),
                endpoint("POST", "/api/v1/signup")));
        var login = endpoint("POST", "/api/v1/auth/login");
        var wrongMethod = new AuthorizationContractVerifier.Endpoint(
                new AuthorizationContractVerifier.Route("GET", login.route().path()),
                login.mapping(), login.handler());
        assertThat(verifier.inspect(wrongMethod).valid()).isFalse();
    }

    @Test
    @DisplayName("Un prefijo parecido a plataforma no hereda sus permisos")
    void doesNotConfusePlatformPrefixes() {
        assertThatThrownBy(() -> verifier.requireValid(List.of(
                endpoint("GET", "/api/v1/platform-other/contract-fixture"))))
                .isInstanceOf(AssertionError.class).hasMessageContaining("Falta una decisión explícita");
    }

    @Test
    @DisplayName("Si el matcher de plataforma se debilita, el verificador lo detecta")
    void detectsMatcherDriftInsteadOfTrustingDuplicatedConfiguration() {
        var weakenedFilter = new AuthorizationFilter(AuthenticatedAuthorizationManager.authenticated());
        var weakenedChain = new FilterChainProxy(new DefaultSecurityFilterChain(
                AnyRequestMatcher.INSTANCE, weakenedFilter));
        var changedVerifier = new AuthorizationContractVerifier(context, weakenedChain);
        assertThatThrownBy(() -> changedVerifier.requireValid(List.of(
                endpoint("GET", "/api/v1/platform/contract-fixture"))))
                .isInstanceOf(AssertionError.class).hasMessageContaining("no limita a SUPER_ADMIN");
    }

    @ParameterizedTest
    @CsvSource({"GET, /api/v1/auth/me", "POST, /api/v1/auth/logout"})
    @DisplayName("Las rutas de sesión no se aceptan como excepciones públicas")
    void sessionRoutesMustRequireAuthentication(String method, String path) {
        var publicFilter = new AuthorizationFilter((authentication, request) -> new AuthorizationDecision(true));
        var publicChain = new FilterChainProxy(new DefaultSecurityFilterChain(
                AnyRequestMatcher.INSTANCE, publicFilter));
        var publicVerifier = new AuthorizationContractVerifier(context, publicChain);
        assertThatThrownBy(() -> publicVerifier.requireValid(List.of(endpoint(method, path))))
                .isInstanceOf(AssertionError.class).hasMessageContaining("lista pública");

        var sessionFilter = new AuthorizationFilter(AuthenticatedAuthorizationManager.authenticated());
        var sessionChain = new FilterChainProxy(new DefaultSecurityFilterChain(
                AnyRequestMatcher.INSTANCE, sessionFilter));
        var sessionVerifier = new AuthorizationContractVerifier(context, sessionChain);
        sessionVerifier.requireValid(List.of(endpoint(method, path)));
        assertThat(sessionVerifier.inspect(endpoint(method, path)).allowed()).hasSize(UserRole.values().length)
                .doesNotContain("ANONYMOUS");
    }

    @Test
    @DisplayName("Se descubre la condición folio sin inventar otro endpoint")
    void preservesMappingConditionsAndIncludesSessionRoutes() {
        assertThat(endpoint("GET", "/api/v1/tickets").mapping().getParamsCondition().toString())
                .contains("folio");
        assertThat(endpoint("GET", "/api/v1/auth/me")).isNotNull();
        assertThat(endpoint("POST", "/api/v1/auth/logout")).isNotNull();
        assertThat(endpoints).anyMatch(e -> e.route().path().equals("/actuator/health"));
        assertThat(endpoints).anyMatch(e -> e.route().path().equals("/v3/api-docs"));
    }

    @ParameterizedTest
    @EnumSource(UserRole.class)
    @DisplayName("Los filtros y proxies reales confirman permisos para los cinco roles")
    void actualHttpAuthorizationMatchesVerifier(UserRole role) throws Exception {
        Cookie cookie = cookie(role);
        for (String path : List.of("/api/v1/contract-fixtures/method",
                "/api/v1/contract-fixtures/composed", "/api/v1/contract-fixtures/inherited",
                "/api/v1/contract-class", "/api/v1/contract-class/override",
                "/api/v1/platform/contract-fixture")) {
            Set<String> allowed = verifier.inspect(endpoint("GET", path)).allowed();
            mockMvc.perform(get(path).cookie(cookie))
                    .andExpect(status().is(allowed.contains(role.name()) ? 200 : 403));
        }
    }

    @Test
    @DisplayName("Los endpoints protegidos rechazan peticiones anónimas")
    void actualHttpRejectsAnonymousRequests() throws Exception {
        mockMvc.perform(get("/api/v1/contract-fixtures/method")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/platform/contract-fixture")).andExpect(status().isUnauthorized());
    }

    private AuthorizationContractVerifier.Endpoint endpoint(String method, String path) {
        List<AuthorizationContractVerifier.Endpoint> matches = endpoints.stream()
                .filter(e -> e.route().equals(new AuthorizationContractVerifier.Route(method, path))).toList();
        assertThat(matches).as("Mapping registrado %s %s", method, path).hasSize(1);
        return matches.getFirst();
    }

    private Cookie cookie(UserRole role) {
        User user = User.builder().role(role).build();
        user.setId(UUID.randomUUID());
        user.setTenantId(UUID.randomUUID());
        return new Cookie("access_token", jwtService.generateAccessToken(user));
    }
}
