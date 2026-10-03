package com.callejon9.config;

import com.callejon9.auth.service.JwtService;
import com.callejon9.user.domain.User;
import com.callejon9.user.domain.UserRole;
import jakarta.servlet.http.Cookie;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static com.callejon9.user.domain.UserRole.ADMIN;
import static com.callejon9.user.domain.UserRole.CASHIER;
import static com.callejon9.user.domain.UserRole.KITCHEN;
import static com.callejon9.user.domain.UserRole.SUPER_ADMIN;
import static com.callejon9.user.domain.UserRole.WAITER;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Matriz rol x endpoint de las lecturas de la API. Cada fila es la regla
 * aceptada en CAL-5; un rol fuera de la fila recibe 403 antes de tocar datos.
 *
 * <p>El token lleva un tenant aleatorio sin filas, asi que un acceso permitido
 * responde 200 con listas vacias o 404 cuando la ruta pide un id: ambos
 * prueban que la peticion paso la autorizacion. El aislamiento entre
 * restaurantes es otra capa y se prueba aparte en TenantIsolationTest.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Matriz de autorizacion de la API")
class AuthorizationMatrixTest {

    private static final Set<UserRole> OPERATIONAL = EnumSet.of(ADMIN, WAITER, KITCHEN, CASHIER);
    private static final String RANDOM_ID = UUID.randomUUID().toString();

    private record Rule(String path, int allowedStatus, Set<UserRole> roles) {
    }

    private static final List<Rule> READ_RULES = List.of(
            new Rule("/api/v1/analytics", 200, EnumSet.of(ADMIN)),
            new Rule("/api/v1/sales", 200, EnumSet.of(ADMIN, CASHIER)),
            new Rule("/api/v1/tickets/" + RANDOM_ID, 404, EnumSet.of(ADMIN, CASHIER)),
            new Rule("/api/v1/tickets?folio=TCK-000000000000", 404, EnumSet.of(ADMIN, CASHIER)),
            new Rule("/api/v1/tickets/" + RANDOM_ID + "/pdf", 404, EnumSet.of(ADMIN, CASHIER)),
            new Rule("/api/v1/orders", 200, EnumSet.of(ADMIN, WAITER, CASHIER)),
            new Rule("/api/v1/orders/" + RANDOM_ID, 404, EnumSet.of(ADMIN, WAITER, CASHIER)),
            new Rule("/api/v1/kitchen/orders", 200, EnumSet.of(ADMIN, KITCHEN)),
            new Rule("/api/v1/tables", 200, OPERATIONAL),
            new Rule("/api/v1/products", 200, OPERATIONAL),
            new Rule("/api/v1/categories", 200, OPERATIONAL),
            new Rule("/api/v1/inventory/items", 200, EnumSet.of(ADMIN, KITCHEN)),
            new Rule("/api/v1/inventory/movements", 200, EnumSet.of(ADMIN, KITCHEN)),
            new Rule("/api/v1/users", 200, EnumSet.of(ADMIN)),
            new Rule("/api/v1/platform/plans", 200, EnumSet.of(SUPER_ADMIN)));

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtService jwtService;

    static Stream<Arguments> readMatrix() {
        return READ_RULES.stream().flatMap(rule -> Arrays.stream(UserRole.values())
                .map(role -> Arguments.of(role, rule.path(),
                        rule.roles().contains(role) ? rule.allowedStatus() : 403)));
    }

    static Stream<Arguments> anonymousAccess() {
        return Stream.of(
                // Publicas: llegan al controller sin cookie y fallan por validacion, no por sesion.
                Arguments.of(HttpMethod.POST, "/api/v1/auth/login", 400),
                Arguments.of(HttpMethod.POST, "/api/v1/signup", 400),
                Arguments.of(HttpMethod.GET, "/actuator/health", 200),
                Arguments.of(HttpMethod.GET, "/v3/api-docs", 200),
                // Bajo /auth pero con sesion obligatoria.
                Arguments.of(HttpMethod.GET, "/api/v1/auth/me", 401),
                Arguments.of(HttpMethod.POST, "/api/v1/auth/logout", 401),
                // Una ruta de negocio cualquiera.
                Arguments.of(HttpMethod.GET, "/api/v1/sales", 401));
    }

    private String tokenFor(UserRole role) {
        User user = User.builder()
                .email("matriz@demo.com").passwordHash("x").fullName("Matriz")
                .role(role).active(true).build();
        user.setId(UUID.randomUUID());
        user.setTenantId(UUID.randomUUID());
        return jwtService.generateAccessToken(user);
    }

    @ParameterizedTest(name = "{0} GET {1} -> {2}")
    @MethodSource("readMatrix")
    @DisplayName("cada rol obtiene exactamente las lecturas que su fila concede")
    void eachRoleGetsOnlyItsReads(UserRole role, String path, int expectedStatus) throws Exception {
        mockMvc.perform(request(HttpMethod.GET, path)
                        .cookie(new Cookie("access_token", tokenFor(role))))
                .andExpect(status().is(expectedStatus));
    }

    @ParameterizedTest(name = "{0} {1} sin cookie -> {2}")
    @MethodSource("anonymousAccess")
    @DisplayName("sin cookie solo responden login, signup, health y OpenAPI")
    void onlyTheDocumentedPublicRoutesAnswerWithoutSession(HttpMethod method, String path,
            int expectedStatus) throws Exception {
        mockMvc.perform(request(method, path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().is(expectedStatus));
    }
}
