package com.callejon9.shared.throttle;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Cupo de operaciones por llave dentro de una ventana deslizante. No sabe que
 * es la llave (una IP, una cuenta) ni que operacion cuenta: eso lo decide
 * quien lo usa.
 *
 * <p>{@link #tryAcquire} comprueba y registra en un solo paso, bajo el candado
 * de la llave: dos peticiones simultaneas de la misma llave no pueden leer
 * ambas "queda un lugar" y entrar las dos. Cada cola solo se toca dentro de
 * {@code compute}/{@code computeIfPresent} del mapa, que es ese candado.
 *
 * <p>El estado vive en memoria del proceso. Alcanza para una sola instancia
 * del backend; con varias, cada una contaria por su lado y el limite efectivo
 * se multiplicaria. En ese caso el contador debe moverse a un almacen
 * compartido (Redis o una tabla).
 */
public class SlidingWindowRateLimiter {

    /** Cada cuantas operaciones se purgan las llaves sin uso reciente. */
    private static final int CLEANUP_EVERY = 1_000;

    private final int maxPerWindow;
    private final Duration window;
    private final Clock clock;

    private final Map<String, Deque<Instant>> usesByKey = new ConcurrentHashMap<>();
    private final AtomicInteger operations = new AtomicInteger();

    public SlidingWindowRateLimiter(int maxPerWindow, Duration window, Clock clock) {
        if (maxPerWindow < 1) {
            throw new IllegalArgumentException("El cupo debe ser de al menos 1.");
        }
        if (window.isNegative() || window.isZero()) {
            throw new IllegalArgumentException("La ventana debe ser positiva.");
        }
        this.maxPerWindow = maxPerWindow;
        this.window = window;
        this.clock = clock;
    }

    /**
     * Si la llave aun tiene cupo, registra el uso y devuelve cero. Si no, no
     * registra nada y devuelve cuanto falta para que se libere un lugar.
     */
    public Duration tryAcquire(String key) {
        Instant now = clock.instant();
        Duration[] wait = new Duration[1];
        // compute corre bajo el candado de la llave en el mapa: la purga
        // (computeIfPresent) no puede sacar la cola entre que se lee y se
        // registra el uso, cosa que si podia pasar con computeIfAbsent seguido
        // de un synchronized aparte.
        usesByKey.compute(key, (ignored, existing) -> {
            Deque<Instant> uses = existing != null ? existing : new ArrayDeque<>();
            prune(uses, now);
            if (uses.size() < maxPerWindow) {
                uses.addLast(now);
                wait[0] = Duration.ZERO;
            } else {
                // Se libera cuando el uso mas antiguo de la ventana sale de ella.
                wait[0] = Duration.between(now, uses.peekFirst().plus(window));
            }
            return uses;
        });
        maybeCleanUp(now);
        return wait[0];
    }

    private void prune(Deque<Instant> uses, Instant now) {
        Instant cutoff = now.minus(window);
        while (!uses.isEmpty() && !uses.peekFirst().isAfter(cutoff)) {
            uses.removeFirst();
        }
    }

    /** Evita que el mapa crezca sin limite con llaves que ya no bloquean nada. */
    private void maybeCleanUp(Instant now) {
        if (operations.incrementAndGet() % CLEANUP_EVERY != 0) {
            return;
        }
        for (String key : usesByKey.keySet()) {
            // Devolver null quita la llave, atomicamente respecto de tryAcquire.
            usesByKey.computeIfPresent(key, (ignored, uses) -> {
                prune(uses, now);
                return uses.isEmpty() ? null : uses;
            });
        }
    }
}
