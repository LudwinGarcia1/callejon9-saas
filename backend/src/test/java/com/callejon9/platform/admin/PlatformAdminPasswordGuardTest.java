package com.callejon9.platform.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.callejon9.Callejon9Application;
import com.callejon9.tenancy.TenantContext;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Arrays;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.Banner;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * CAL-109: con el perfil prod, la cuenta sembrada por V6 no puede quedar con la contrasena
 * publicada.
 *
 * <p>Cada caso arranca la aplicacion completa con {@code test} y {@code prod} sobre la base de la
 * suite, como {@code StartupFailureTest}. El contexto de esta clase, con el perfil {@code test},
 * prepara y lee el hash como lo haria la aplicacion: con {@code callejon9_app} y el tenant {@code
 * platform} publicado por {@link TenantContext}. Despues de cada caso se restaura el hash de V6.
 *
 * <p>Solo corre con el contenedor: prod rechaza las contrasenas de desarrollo de la base externa,
 * igual que en {@code ProductionProfileTest}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
@DisabledIfSystemProperty(named = "test.database.mode", matches = "external")
@DisabledIfEnvironmentVariable(named = "TEST_DATABASE_MODE", matches = "external")
@DisplayName("CAL-109: contrasena publicada del super-administrador")
class PlatformAdminPasswordGuardTest {

    /** El hash que siembra V6__seed_platform_admin.sql. */
    private static final String PUBLISHED_HASH =
            "$2b$12$PQi8j9RGaLfN33EInKHT4uWi9ToCh6ZH9YiwQTuozkfuOa4T1URFy";

    private static final String JWT_SECRET = "prueba-cal-109-con-un-secreto-jwt-de-mas-de-32-bytes";
    private static final String ORIGIN = "https://callejon9.example";

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private ApplicationContext context;
    @Autowired private MockMvc mockMvc;

    private UUID platformTenant;

    @BeforeEach
    void startFromPublishedSeed() {
        platformTenant =
                jdbcTemplate.queryForObject(
                        "SELECT id FROM tenants WHERE slug = ?",
                        UUID.class,
                        PlatformAdminPasswordGuard.PLATFORM_SLUG);
        setHash(PUBLISHED_HASH);
    }

    @AfterEach
    void restorePublishedSeed() {
        setHash(PUBLISHED_HASH);
        TenantContext.clear();
    }

    @Test
    @DisplayName(
            "sin PLATFORM_ADMIN_PASSWORD, prod no arranca y el mensaje nombra la variable sin la contrasena")
    void publishedPasswordWithoutVariableStopsStartup(CapturedOutput output) {
        Throwable failure = catchThrowable(() -> startProduction().close());

        assertThat(failure).as("el arranque debia fallar").isNotNull();
        assertThat(NestedExceptionUtils.getMostSpecificCause(failure).getMessage())
                .contains("PLATFORM_ADMIN_PASSWORD", PlatformAdminPasswordGuard.ADMIN_EMAIL)
                .doesNotContain(PlatformAdminPasswordGuard.PUBLISHED_PASSWORD);
        assertThat(output.getAll())
                .doesNotContain(PlatformAdminPasswordGuard.PUBLISHED_PASSWORD, PUBLISHED_HASH);
        assertThat(hash()).isEqualTo(PUBLISHED_HASH);
    }

    @Test
    @DisplayName(
            "con PLATFORM_ADMIN_PASSWORD valida, reemplaza el hash: la publicada da 401 y la nueva 200")
    void validVariableReplacesPublishedPassword(CapturedOutput output) throws Exception {
        String newPassword = "admin-" + UUID.randomUUID();

        try (ConfigurableApplicationContext production =
                startProduction("--app.platform-admin.password=" + newPassword)) {
            // La aplicacion opera como callejon9_app, sin privilegios para saltarse RLS.
            assertThat(
                            production
                                    .getBean(JdbcTemplate.class)
                                    .queryForObject("SELECT current_user", String.class))
                    .isEqualTo("callejon9_app");

            int port =
                    Integer.parseInt(
                            production.getEnvironment().getRequiredProperty("local.server.port"));
            assertThat(login(port, PlatformAdminPasswordGuard.PUBLISHED_PASSWORD)).isEqualTo(401);
            assertThat(login(port, newPassword)).isEqualTo(200);
        }

        String replaced = hash();
        assertThat(replaced).isNotEqualTo(PUBLISHED_HASH);
        assertThat(passwordEncoder.matches(newPassword, replaced)).isTrue();
        assertThat(output.getAll()).doesNotContain(newPassword, replaced, PUBLISHED_HASH);
    }

    @Test
    @DisplayName(
            "una PLATFORM_ADMIN_PASSWORD corta o publicada detiene el arranque sin modificar la base")
    void invalidVariableStopsStartupWithoutTouchingTheDatabase() {
        for (String invalid :
                new String[] {"corta-de-15-car", PlatformAdminPasswordGuard.PUBLISHED_PASSWORD}) {
            Throwable failure =
                    catchThrowable(() ->
                            startProduction("--app.platform-admin.password=" + invalid).close());

            assertThat(failure)
                    .as("el arranque debia fallar con '%s'", invalid.length())
                    .isNotNull();
            assertThat(NestedExceptionUtils.getMostSpecificCause(failure).getMessage())
                    .contains("PLATFORM_ADMIN_PASSWORD")
                    .doesNotContain(invalid);
            assertThat(hash()).isEqualTo(PUBLISHED_HASH);
        }
    }

    @Test
    @DisplayName("si la contrasena ya se roto, no la cambia con ni sin PLATFORM_ADMIN_PASSWORD")
    void rotatedPasswordIsNeverOverwritten() {
        String rotatedHash = passwordEncoder.encode("rotada-" + UUID.randomUUID());
        setHash(rotatedHash);

        try (ConfigurableApplicationContext production = startProduction()) {
            assertThat(production.isActive()).isTrue();
        }
        assertThat(hash()).isEqualTo(rotatedHash);

        try (ConfigurableApplicationContext production =
                startProduction("--app.platform-admin.password=otra-" + UUID.randomUUID())) {
            assertThat(production.isActive()).isTrue();
        }
        assertThat(hash()).isEqualTo(rotatedHash);
    }

    @Test
    @DisplayName("fuera de prod el componente no existe y la contrasena documentada sigue entrando")
    void defaultProfileKeepsDocumentedCredentials() throws Exception {
        assertThat(context.getBeansOfType(PlatformAdminPasswordGuard.class)).isEmpty();

        mockMvc.perform(
                        post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(loginBody(PlatformAdminPasswordGuard.PUBLISHED_PASSWORD)))
                .andExpect(status().isOk());
        assertThat(hash()).isEqualTo(PUBLISHED_HASH);
    }

    private static ConfigurableApplicationContext startProduction(String... args) {
        // Como argumentos: tienen prioridad sobre application*.yml.
        Stream<String> base =
                Stream.of(
                        "--server.port=0",
                        "--app.jwt.secret=" + JWT_SECRET,
                        "--app.realtime.allowed-origins=" + ORIGIN);
        return new SpringApplicationBuilder(Callejon9Application.class)
                .profiles("test", "prod")
                .bannerMode(Banner.Mode.OFF)
                .run(Stream.concat(base, Arrays.stream(args)).toArray(String[]::new));
    }

    private static int login(int port, String password) throws Exception {
        HttpRequest request =
                HttpRequest.newBuilder(
                                URI.create("http://localhost:" + port + "/api/v1/auth/login"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(loginBody(password)))
                        .build();
        try (HttpClient client = HttpClient.newHttpClient()) {
            return client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
        }
    }

    private static String loginBody(String password) {
        return """
        {"slug":"%s","email":"%s","password":"%s"}
        """
                .formatted(
                        PlatformAdminPasswordGuard.PLATFORM_SLUG,
                        PlatformAdminPasswordGuard.ADMIN_EMAIL,
                        password);
    }

    private String hash() {
        return TenantContext.callAs(
                platformTenant,
                () ->
                        transactionTemplate.execute(status ->
                                jdbcTemplate.queryForObject(
                                        "SELECT password_hash FROM users WHERE email = ?",
                                        String.class,
                                        PlatformAdminPasswordGuard.ADMIN_EMAIL)));
    }

    private void setHash(String passwordHash) {
        int updated =
                TenantContext.callAs(
                        platformTenant,
                        () ->
                                transactionTemplate.execute(status ->
                                        jdbcTemplate.update(
                                                "UPDATE users SET password_hash = ? WHERE email = ?",
                                                passwordHash,
                                                PlatformAdminPasswordGuard.ADMIN_EMAIL)));
        assertThat(updated).as("la cuenta sembrada por V6 debe existir").isEqualTo(1);
    }
}
