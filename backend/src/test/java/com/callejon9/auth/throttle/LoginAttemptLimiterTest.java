package com.callejon9.auth.throttle;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Limitador de intentos de login")
class LoginAttemptLimiterTest {

    private static final Duration WINDOW = Duration.ofMinutes(15);

    /** Reloj que la prueba adelanta a mano. */
    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-10-03T12:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private final MutableClock clock = new MutableClock();
    private final LoginAttemptLimiter limiter = new LoginAttemptLimiter(3, 5, WINDOW, clock);

    private void fail(String ip, String email) {
        limiter.checkAllowed(ip, "la-esquina", email);
        limiter.recordFailure(ip, "la-esquina", email);
    }

    @Test
    void allowsAttemptsBelowTheAccountLimit() {
        fail("10.0.0.1", "ana@esquina.mx");
        fail("10.0.0.1", "ana@esquina.mx");

        assertThatCode(() -> limiter.checkAllowed("10.0.0.1", "la-esquina", "ana@esquina.mx"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("bloquea la cuenta al llegar al limite y avisa cuanto falta")
    void blocksTheAccountAtTheLimit() {
        fail("10.0.0.1", "ana@esquina.mx");
        clock.advance(Duration.ofMinutes(5));
        fail("10.0.0.2", "ana@esquina.mx");
        fail("10.0.0.3", "ana@esquina.mx");

        assertThatThrownBy(() -> limiter.checkAllowed("10.0.0.4", "la-esquina", "ANA@esquina.mx"))
                .isInstanceOfSatisfying(LoginThrottledException.class, exception ->
                        // El primer fallo salio hace 5 minutos: faltan 10.
                        assertThat(exception.getRetryAfter()).isEqualTo(Duration.ofMinutes(10)));
    }

    @Test
    @DisplayName("la ventana es deslizante: el bloqueo se levanta cuando el fallo mas viejo expira")
    void unblocksWhenTheOldestFailureLeavesTheWindow() {
        fail("10.0.0.1", "ana@esquina.mx");
        clock.advance(Duration.ofMinutes(5));
        fail("10.0.0.1", "ana@esquina.mx");
        fail("10.0.0.1", "ana@esquina.mx");

        clock.advance(Duration.ofMinutes(10));
        assertThatCode(() -> limiter.checkAllowed("10.0.0.9", "la-esquina", "ana@esquina.mx"))
                .doesNotThrowAnyException();

        // Quedan dos fallos dentro de la ventana: uno mas vuelve a bloquear.
        limiter.recordFailure("10.0.0.9", "la-esquina", "ana@esquina.mx");
        assertThatThrownBy(() -> limiter.checkAllowed("10.0.0.9", "la-esquina", "ana@esquina.mx"))
                .isInstanceOf(LoginThrottledException.class);
    }

    @Test
    void successClearsTheAccountButNotTheIp() {
        fail("10.0.0.1", "ana@esquina.mx");
        fail("10.0.0.1", "ana@esquina.mx");
        limiter.recordSuccess("la-esquina", "ana@esquina.mx");
        fail("10.0.0.1", "ana@esquina.mx");
        fail("10.0.0.1", "ana@esquina.mx");

        // Dos fallos de cuenta desde el acierto: sigue permitida.
        assertThatCode(() -> limiter.checkAllowed("10.0.0.2", "la-esquina", "ana@esquina.mx"))
                .doesNotThrowAnyException();

        // Pero la IP ya acumula 4; uno mas la bloquea para cualquier cuenta.
        fail("10.0.0.1", "otra@esquina.mx");
        assertThatThrownBy(() -> limiter.checkAllowed("10.0.0.1", "la-esquina", "nueva@esquina.mx"))
                .isInstanceOf(LoginThrottledException.class);
    }

    @Test
    @DisplayName("el limite por IP frena el ataque a muchas cuentas distintas")
    void blocksAnIpThatSpraysManyAccounts() {
        for (int account = 0; account < 5; account++) {
            fail("10.0.0.1", "cuenta" + account + "@esquina.mx");
        }

        assertThatThrownBy(() -> limiter.checkAllowed("10.0.0.1", "la-esquina", "otra@esquina.mx"))
                .isInstanceOf(LoginThrottledException.class);
        assertThatCode(() -> limiter.checkAllowed("10.0.0.2", "la-esquina", "otra@esquina.mx"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("el mismo correo en otro restaurante es otra cuenta")
    void accountsAreScopedByRestaurant() {
        fail("10.0.0.1", "ana@esquina.mx");
        fail("10.0.0.2", "ana@esquina.mx");
        fail("10.0.0.3", "ana@esquina.mx");

        assertThatCode(() -> limiter.checkAllowed("10.0.0.4", "el-portal", "ana@esquina.mx"))
                .doesNotThrowAnyException();
    }
}
