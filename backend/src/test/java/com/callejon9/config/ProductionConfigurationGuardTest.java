package com.callejon9.config;

import com.callejon9.auth.service.JwtService;
import com.callejon9.auth.web.AuthCookies;
import com.callejon9.tenancy.TenantFilter;
import com.callejon9.user.domain.User;
import com.callejon9.user.domain.UserRole;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.Banner;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.http.ResponseCookie;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Arranca la aplicacion con los {@code application*.yml} reales pero solo con
 * los beans de sesion: no necesita base de datos, asi que cubre todos los
 * casos negativos sin Docker.
 *
 * <p>El entorno se arma a mano, sin variables del sistema ni propiedades de
 * la JVM: CI define JWT_SECRET y las contrasenas de desarrollo, y la prueba de
 * "variable ausente" tiene que poder verla ausente.
 */
@ExtendWith(OutputCaptureExtension.class)
@DisplayName("Configuracion del perfil prod")
class ProductionConfigurationGuardTest {

    private static final String STRONG_SECRET = "prueba-" + UUID.randomUUID() + "-" + UUID.randomUUID();
    private static final String ORIGIN = "https://callejon9.example";

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(JwtProperties.class)
    @Import({ProductionConfigurationGuard.class, JwtService.class, AuthCookies.class})
    static class SessionBeans {
    }

    private static Map<String, Object> productionVariables() {
        Map<String, Object> variables = new HashMap<>();
        variables.put("JWT_SECRET", STRONG_SECRET);
        variables.put("DB_APP_PASSWORD", "app-" + UUID.randomUUID());
        variables.put("DB_OWNER_PASSWORD", "owner-" + UUID.randomUUID());
        variables.put("APP_ALLOWED_ORIGINS", ORIGIN);
        return variables;
    }

    private static ConfigurableApplicationContext start(Map<String, Object> variables, String... profiles) {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        // Reemplaza las variables reales con el mismo tipo de fuente, para que
        // nombres como SPRINGDOC_SWAGGERUI_ENABLED se resuelvan igual que en
        // App Service.
        environment.getPropertySources().replace(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                new SystemEnvironmentPropertySource(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, variables));
        return new SpringApplicationBuilder(SessionBeans.class)
                .environment(environment)
                .web(WebApplicationType.NONE)
                .bannerMode(Banner.Mode.OFF)
                .profiles(profiles)
                .run();
    }

    private static String startupFailure(Map<String, Object> variables, String... profiles) {
        Throwable failure = catchThrowable(() -> start(variables, profiles).close());
        assertThat(failure).as("el arranque debia fallar").isNotNull();
        return NestedExceptionUtils.getMostSpecificCause(failure).getMessage();
    }

    @Test
    @DisplayName("sin secretos, prod no arranca y el mensaje nombra cada variable que falta")
    void missingSecretsStopStartup() {
        String message = startupFailure(Map.of(), "prod");

        assertThat(message).contains(
                "JWT_SECRET no esta definida",
                "DB_APP_PASSWORD no esta definida",
                "DB_OWNER_PASSWORD no esta definida",
                "APP_ALLOWED_ORIGINS no esta definida");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "dev-only-secret-change-me-min-32-bytes-long!!",
            "ci-only-secret-at-least-32-bytes-long!!",
            "dev-only-secret-change-me-min-32-bytes-long!! ",
            "dev-only-secret-change-me-min-32-bytes-long!!\r",
            "secreto-corto"})
    @DisplayName("un JWT_SECRET publicado o corto detiene el arranque sin aparecer en el log")
    void insecureJwtSecretStopsStartup(String secret, CapturedOutput output) {
        Map<String, Object> variables = productionVariables();
        variables.put("JWT_SECRET", secret);

        String message = startupFailure(variables, "prod");

        assertThat(message).contains("JWT_SECRET").doesNotContain(secret.strip());
        assertThat(output.getAll()).doesNotContain(secret.strip());
    }

    @Test
    @DisplayName("las contrasenas de desarrollo de la base detienen el arranque sin aparecer en el log")
    void developmentDatabasePasswordsStopStartup(CapturedOutput output) {
        Map<String, Object> variables = productionVariables();
        variables.put("DB_APP_PASSWORD", "app_dev_pwd");
        // Con espacio final sigue siendo la contrasena publicada.
        variables.put("DB_OWNER_PASSWORD", "owner_dev_pwd ");

        String message = startupFailure(variables, "prod");

        assertThat(message).contains(
                "DB_APP_PASSWORD usa la contrasena de desarrollo",
                "DB_OWNER_PASSWORD usa la contrasena de desarrollo");
        assertThat(output.getAll()).doesNotContain("app_dev_pwd", "owner_dev_pwd");
    }

    @Test
    @DisplayName("prod no admite cookies sin Secure ni origenes sin https")
    void insecureTransportStopsStartup() {
        Map<String, Object> variables = productionVariables();
        variables.put("app.auth.cookie-secure", "false");
        variables.put("APP_ALLOWED_ORIGINS", ORIGIN + ",http://localhost:3000");

        String message = startupFailure(variables, "prod");

        assertThat(message).contains(
                "app.auth.cookie-secure debe ser true",
                "APP_ALLOWED_ORIGINS solo admite origenes https://");
    }

    @ParameterizedTest
    @ValueSource(strings = {"demo", "dev"})
    @DisplayName("un perfil de desarrollo no puede activarse junto a prod")
    void localProfileCannotRunAlongsideProduction(String localProfile) {
        String message = startupFailure(productionVariables(), "prod", localProfile);

        assertThat(message).contains("[" + localProfile + "]", "no pueden activarse junto a prod");
    }

    @Test
    @DisplayName("con secretos validos prod arranca, emite cookies Secure y no advierte")
    void validProductionConfigurationStarts(CapturedOutput output) {
        try (ConfigurableApplicationContext context = start(productionVariables(), "prod")) {
            List<ResponseCookie> cookies = context.getBean(AuthCookies.class)
                    .issue("access", "refresh", Instant.now().plusSeconds(60));
            assertThat(cookies).extracting(ResponseCookie::getName)
                    .containsExactly(TenantFilter.ACCESS_TOKEN_COOKIE, AuthCookies.REFRESH_TOKEN_COOKIE);
            assertThat(cookies).allSatisfy(cookie -> {
                assertThat(cookie.isSecure()).isTrue();
                assertThat(cookie.isHttpOnly()).isTrue();
                assertThat(cookie.getSameSite()).isEqualTo("Strict");
            });

            JwtService jwtService = context.getBean(JwtService.class);
            User user = User.builder().email("a@b.mx").passwordHash("x").fullName("A")
                    .role(UserRole.ADMIN).active(true).build();
            user.setId(UUID.randomUUID());
            user.setTenantId(UUID.randomUUID());
            UUID sessionId = UUID.randomUUID();
            assertThat(jwtService.parse(jwtService.generateAccessToken(user, sessionId)).sessionId())
                    .isEqualTo(sessionId);

            var environment = context.getEnvironment();
            assertThat(environment.getProperty("management.endpoints.web.exposure.include")).isEqualTo("health");
            assertThat(environment.getProperty("management.endpoint.health.show-details")).isEqualTo("never");
            assertThat(environment.getProperty("springdoc.api-docs.enabled", Boolean.class)).isFalse();
            assertThat(environment.getProperty("springdoc.swagger-ui.enabled", Boolean.class)).isFalse();
            assertThat(environment.getProperty("server.forward-headers-strategy")).isEqualTo("none");
        }
        assertThat(output.getAll()).doesNotContain("NO apta para produccion").doesNotContain(STRONG_SECRET);
    }

    @Test
    @DisplayName("Terraform puede encender OpenAPI y Swagger UI en prod con variables de entorno")
    void swaggerCanBeEnabledThroughEnvironmentVariables() {
        Map<String, Object> variables = productionVariables();
        variables.put("SPRINGDOC_APIDOCS_ENABLED", "true");
        variables.put("SPRINGDOC_SWAGGERUI_ENABLED", "true");
        try (ConfigurableApplicationContext context = start(variables, "prod")) {
            var environment = context.getEnvironment();
            assertThat(environment.getProperty("springdoc.api-docs.enabled", Boolean.class)).isTrue();
            assertThat(environment.getProperty("springdoc.swagger-ui.enabled", Boolean.class)).isTrue();
        }
    }

    @Test
    @DisplayName("demo sigue arrancando sin variables, con cookies sin Secure y una advertencia visible")
    void demoStartsWithVisibleWarning(CapturedOutput output) {
        try (ConfigurableApplicationContext context = start(Map.of(), "demo")) {
            assertThat(context.getBean(AuthCookies.class).clear())
                    .allSatisfy(cookie -> assertThat(cookie.isSecure()).isFalse());
        }
        assertThat(output.getAll())
                .contains("Configuracion NO apta para produccion (perfiles [demo], JWT_SECRET publicado en el repositorio)")
                .doesNotContain("dev-only-secret-change-me-min-32-bytes-long!!");
    }

    @Test
    @DisplayName("sin perfil, el secreto de desarrollo arranca con advertencia")
    void defaultProfileWarnsAboutPublishedSecret(CapturedOutput output) {
        try (ConfigurableApplicationContext context = start(Map.of())) {
            assertThat(context.getBean(JwtService.class)).isNotNull();
        }
        assertThat(output.getAll())
                .contains("Configuracion NO apta para produccion (perfiles por defecto, JWT_SECRET publicado en el repositorio)");
    }
}
