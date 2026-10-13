package com.callejon9.user;

import com.callejon9.auth.service.AuthService;
import com.callejon9.auth.service.RefreshTokenService;
import com.callejon9.platform.tenant.domain.Tenant;
import com.callejon9.platform.tenant.service.TenantOnboardingService;
import com.callejon9.support.TestSessions;
import com.callejon9.tenancy.TenantContext;
import com.callejon9.user.domain.User;
import com.callejon9.user.domain.UserRole;
import com.callejon9.user.service.UserService;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Una sesion que nace mientras se desactiva al usuario no puede sobrevivir a
 * la baja. Bajo READ COMMITTED, el UPDATE que revoca las sesiones no ve un
 * token insertado por otra transaccion que confirme despues de que la
 * sentencia empezo; por eso renovacion y login leen al usuario con FOR SHARE,
 * lo que las ordena con la desactivacion sobre la fila del usuario.
 *
 * <p>Se repite porque el entrelazado varia entre corridas. En cualquier orden
 * el resultado debe ser el mismo: la baja tiene exito, ninguna transaccion
 * muere por interbloqueo y el usuario no conserva ningun token vivo.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Carrera entre desactivar un usuario y abrir o renovar su sesion")
class DeactivationSessionRaceTest {

    private static final String SLUG = "carrera-baja";

    @Autowired private TenantOnboardingService onboardingService;
    @Autowired private AuthService authService;
    @Autowired private RefreshTokenService refreshTokenService;
    @Autowired private UserService userService;
    @Autowired private TestSessions testSessions;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private DataSource dataSource;

    private Tenant tenant;
    private UUID adminId;
    private UUID waiterId;
    private String waiterEmail;

    @BeforeEach
    void seed() {
        tenant = onboardingService.onboard("Carrera Baja", SLUG,
                "admin@carrera-baja.com", "Admin", "Secreto123!", "FREE");
        adminId = authService.authenticate(SLUG, "admin@carrera-baja.com", "Secreto123!").user().getId();

        User waiter = testSessions.newUserIn(tenant.getId(), UserRole.WAITER);
        waiterId = waiter.getId();
        TenantContext.callAs(tenant.getId(), () -> transactionTemplate.execute(status ->
                jdbcTemplate.update("UPDATE users SET password_hash = ? WHERE id = ?",
                        passwordEncoder.encode("Secreto123!"), waiterId)));
        waiterEmail = waiter.getEmail();
    }

    @AfterEach
    void cleanUp() {
        TenantContext.clear();
        jdbcTemplate.update("DELETE FROM tenants WHERE slug = ?", SLUG);
    }

    @RepeatedTest(10)
    @DisplayName("renovar mientras se desactiva: la baja gana y no queda ningun token vivo")
    void refreshRacingDeactivationLeavesNoLiveSession() throws InterruptedException {
        String refreshToken = authService.authenticate(SLUG, waiterEmail, "Secreto123!").refreshToken();

        List<Throwable> unexpected = race(
                () -> {
                    try {
                        refreshTokenService.rotate(refreshToken);
                    } catch (BadCredentialsException rejectedBecauseDeactivated) {
                        // Resultado valido si la baja confirmo primero.
                    }
                },
                this::deactivateWaiter);

        assertThat(unexpected).as("ninguna transaccion debe fallar (p. ej. por interbloqueo)").isEmpty();
        assertThat(liveTokensOfWaiter()).as("tokens vivos del usuario desactivado").isZero();
    }

    @RepeatedTest(10)
    @DisplayName("iniciar sesion mientras se desactiva: la baja gana y no queda ningun token vivo")
    void loginRacingDeactivationLeavesNoLiveSession() throws InterruptedException {
        List<Throwable> unexpected = race(
                () -> {
                    try {
                        authService.authenticate(SLUG, waiterEmail, "Secreto123!");
                    } catch (BadCredentialsException rejectedBecauseDeactivated) {
                        // Resultado valido si la baja confirmo primero.
                    }
                },
                this::deactivateWaiter);

        assertThat(unexpected).as("ninguna transaccion debe fallar (p. ej. por interbloqueo)").isEmpty();
        assertThat(liveTokensOfWaiter()).as("tokens vivos del usuario desactivado").isZero();
    }

    /**
     * Version determinista de la carrera del login. Una baja en curso (sin
     * confirmar) se reproduce con una conexion propia que ejecuta las mismas
     * sentencias que {@code UserService.setActive}: marcar al usuario inactivo
     * y revocar sus sesiones. Sin el FOR SHARE, el login no esperaba: insertaba
     * un token que la revocacion ya no podia ver y la sesion sobrevivia a la
     * baja. Con el candado, el login espera a que la baja confirme y la
     * rechaza.
     */
    @Test
    @DisplayName("un login que llega durante una baja en curso espera a que confirme y es rechazado")
    void loginDuringAnInFlightDeactivationWaitsAndIsRejected() throws Exception {
        try (Connection deactivation = dataSource.getConnection()) {
            deactivation.setAutoCommit(false);
            execute(deactivation, "SELECT set_config('app.tenant_id', ?, true)", tenant.getId().toString());
            execute(deactivation, "UPDATE users SET active = false WHERE id = ?", waiterId);
            execute(deactivation, "UPDATE refresh_tokens SET revoked_at = now() "
                    + "WHERE user_id = ? AND revoked_at IS NULL", waiterId);

            CompletableFuture<Throwable> login = CompletableFuture.supplyAsync(() -> {
                try {
                    authService.authenticate(SLUG, waiterEmail, "Secreto123!");
                    return null;
                } catch (Throwable outcome) {
                    return outcome;
                }
            });

            Thread.sleep(500);
            assertThat(login).as("el login debe esperar a que la baja en curso confirme").isNotDone();

            deactivation.commit();
            assertThat(login.get(10, TimeUnit.SECONDS)).isInstanceOf(BadCredentialsException.class);
        }

        assertThat(liveTokensOfWaiter()).as("tokens vivos del usuario desactivado").isZero();
    }

    private static void execute(Connection connection, String sql, Object parameter) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, parameter);
            statement.execute();
        }
    }

    private void deactivateWaiter() {
        TenantContext.callAs(tenant.getId(), () -> userService.setActive(waiterId, false, adminId));
    }

    private int liveTokensOfWaiter() {
        Integer live = TenantContext.callAs(tenant.getId(), () -> transactionTemplate.execute(status ->
                jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM refresh_tokens WHERE user_id = ? AND revoked_at IS NULL",
                        Integer.class, waiterId)));
        return live == null ? 0 : live;
    }

    /** Arranca ambas acciones a la vez y devuelve las excepciones inesperadas. */
    private static List<Throwable> race(Runnable first, Runnable second) throws InterruptedException {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);
        List<Throwable> unexpected = new CopyOnWriteArrayList<>();

        for (Runnable action : List.of(first, second)) {
            new Thread(() -> {
                try {
                    ready.countDown();
                    start.await();
                    action.run();
                } catch (Throwable ex) {
                    unexpected.add(ex);
                } finally {
                    TenantContext.clear();
                    done.countDown();
                }
            }).start();
        }

        assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        assertThat(done.await(15, TimeUnit.SECONDS)).isTrue();
        return unexpected;
    }
}
