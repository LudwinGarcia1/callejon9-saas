package com.callejon9.auth;

import com.callejon9.auth.service.AuthService;
import com.callejon9.auth.service.RefreshTokenService;
import com.callejon9.platform.tenant.service.TenantOnboardingService;
import com.callejon9.tenancy.TenantContext;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Dos peticiones que presentan el mismo refresh token al mismo tiempo pueden
 * leer ambas la fila sin revocar. Solo el UPDATE condicional sobre
 * {@code revoked_at IS NULL} decide quien gana, y ese candado vive en
 * PostgreSQL: por eso la prueba corre contra la base real y no depende de
 * que ambos hilos esten en la misma JVM que el candado.
 *
 * <p>Se repite porque el entrelazado de los hilos varia entre corridas:
 * a veces ambos leen antes del UPDATE (carrera real) y a veces el segundo ya
 * ve el token consumido. En los dos casos debe ganar exactamente uno.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Concurrencia al rotar el mismo refresh token")
class RefreshTokenConcurrencyTest {

    @Autowired private RefreshTokenService refreshTokenService;
    @Autowired private AuthService authService;
    @Autowired private TenantOnboardingService onboardingService;
    @Autowired private JdbcTemplate jdbcTemplate;

    private String refreshToken;

    @BeforeEach
    void seed() {
        onboardingService.onboard("Refresh Carrera", "refresh-carrera",
                "admin@carrera.com", "Admin", "Secreto123!", "FREE");
        refreshToken = authService.authenticate(
                "refresh-carrera", "admin@carrera.com", "Secreto123!").refreshToken();
    }

    @AfterEach
    void cleanUp() {
        TenantContext.clear();
        jdbcTemplate.update("DELETE FROM tenants WHERE slug = 'refresh-carrera'");
    }

    @RepeatedTest(5)
    @DisplayName("dos refresh concurrentes del mismo token: exactamente uno tiene exito")
    void exactlyOneConcurrentRefreshSucceeds() throws InterruptedException {
        int threadCount = 2;
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);
        AtomicInteger successes = new AtomicInteger();
        List<Throwable> failures = new CopyOnWriteArrayList<>();

        Runnable refreshTask = () -> {
            try {
                ready.countDown();
                start.await();
                refreshTokenService.rotate(refreshToken);
                successes.incrementAndGet();
            } catch (Throwable ex) {
                failures.add(ex);
            } finally {
                done.countDown();
            }
        };

        new Thread(refreshTask, "refresh-1").start();
        new Thread(refreshTask, "refresh-2").start();

        assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();

        assertThat(successes.get())
                .as("exactamente una renovacion debe tener exito")
                .isEqualTo(1);
        assertThat(failures).singleElement().isInstanceOf(BadCredentialsException.class);
    }
}
