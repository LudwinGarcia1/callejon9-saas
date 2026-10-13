package com.callejon9.shared.throttle;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Cupo por ventana deslizante")
class SlidingWindowRateLimiterTest {

    private static final Duration WINDOW = Duration.ofHours(1);

    /** Reloj que la prueba adelanta a mano. */
    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-10-10T12:00:00Z");

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
    private final SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter(3, WINDOW, clock);

    @Test
    @DisplayName("permite hasta el cupo y rechaza el siguiente con el tiempo de espera")
    void allowsUpToTheLimitAndThenReportsTheWait() {
        assertThat(limiter.tryAcquire("203.0.113.1")).isZero();
        clock.advance(Duration.ofMinutes(10));
        assertThat(limiter.tryAcquire("203.0.113.1")).isZero();
        assertThat(limiter.tryAcquire("203.0.113.1")).isZero();

        // El primer uso fue hace 10 minutos: se libera en 50.
        assertThat(limiter.tryAcquire("203.0.113.1")).isEqualTo(Duration.ofMinutes(50));
    }

    @Test
    @DisplayName("al expirar la ventana la llave recupera el cupo")
    void quotaReturnsWhenTheWindowExpires() {
        IntStream.range(0, 3).forEach(i -> limiter.tryAcquire("203.0.113.2"));
        assertThat(limiter.tryAcquire("203.0.113.2")).isPositive();

        clock.advance(WINDOW);

        assertThat(limiter.tryAcquire("203.0.113.2")).isZero();
    }

    @Test
    @DisplayName("un intento rechazado no alarga el bloqueo")
    void rejectedAttemptsAreNotRecorded() {
        IntStream.range(0, 3).forEach(i -> limiter.tryAcquire("203.0.113.3"));
        IntStream.range(0, 10).forEach(i -> limiter.tryAcquire("203.0.113.3"));

        clock.advance(WINDOW);

        assertThat(limiter.tryAcquire("203.0.113.3")).isZero();
    }

    @Test
    @DisplayName("cada llave tiene su propio cupo")
    void keysAreIndependent() {
        IntStream.range(0, 3).forEach(i -> limiter.tryAcquire("203.0.113.4"));

        assertThat(limiter.tryAcquire("203.0.113.4")).isPositive();
        assertThat(limiter.tryAcquire("203.0.113.5")).isZero();
    }

    @Test
    @DisplayName("peticiones simultaneas de la misma llave no exceden el cupo")
    void concurrentAcquiresNeverExceedTheLimit() throws Exception {
        int threads = 20;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Callable<Boolean> attempt = () -> {
                start.await();
                return limiter.tryAcquire("203.0.113.6").isZero();
            };
            List<Future<Boolean>> results = IntStream.range(0, threads)
                    .mapToObj(i -> pool.submit(attempt))
                    .toList();
            start.countDown();

            long granted = 0;
            for (Future<Boolean> result : results) {
                if (result.get()) {
                    granted++;
                }
            }
            assertThat(granted).isEqualTo(3);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("la purga periodica conserva las llaves vigentes y quita las vencidas")
    void periodicCleanupKeepsLiveKeysAndDropsExpiredOnes() {
        IntStream.range(0, 3).forEach(i -> limiter.tryAcquire("203.0.113.7"));

        // Suficientes operaciones con otras llaves para disparar la purga.
        IntStream.range(0, 1_000).forEach(i -> limiter.tryAcquire("10.0." + (i / 250) + "." + (i % 250)));
        assertThat(limiter.tryAcquire("203.0.113.7")).as("la llave bloqueada sobrevive a la purga").isPositive();

        clock.advance(WINDOW);
        IntStream.range(0, 1_000).forEach(i -> limiter.tryAcquire("10.1." + (i / 250) + "." + (i % 250)));
        assertThat(limiter.tryAcquire("203.0.113.7")).isZero();
    }

    @Test
    @DisplayName("rechaza configuraciones sin cupo o sin ventana")
    void rejectsInvalidConfiguration() {
        assertThatThrownBy(() -> new SlidingWindowRateLimiter(0, WINDOW, clock))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SlidingWindowRateLimiter(3, Duration.ZERO, clock))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
