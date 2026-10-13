package com.callejon9.auth.throttle;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Freno contra fuerza bruta en el login. Cuenta los intentos fallidos dentro
 * de una ventana deslizante con dos llaves independientes:
 *
 * <ul>
 *   <li><b>Cuenta</b> (restaurante + correo): frena a quien prueba muchas
 *       contrasenas contra la misma persona, aunque cambie de IP.</li>
 *   <li><b>IP</b>: frena a quien prueba una contrasena comun contra muchas
 *       cuentas distintas (password spraying).</li>
 * </ul>
 *
 * <p>La llave de cuenta se arma con lo que el cliente envio, exista o no la
 * cuenta: asi el bloqueo se comporta igual para una cuenta real que para una
 * inventada y no sirve para descubrir correos validos.
 *
 * <p>El estado vive en memoria del proceso. Alcanza para una sola instancia
 * del backend; con varias, cada una contaria por su lado y el limite efectivo
 * se multiplicaria. En ese caso el contador debe moverse a un almacen
 * compartido (Redis o una tabla).
 */
@Component
public class LoginAttemptLimiter {

    /** Cada cuantas operaciones se purgan las llaves sin fallos recientes. */
    private static final int CLEANUP_EVERY = 1_000;

    private final int maxFailuresPerAccount;
    private final int maxFailuresPerIp;
    private final Duration window;
    private final Clock clock;

    private final Map<String, Deque<Instant>> failuresByAccount = new ConcurrentHashMap<>();
    private final Map<String, Deque<Instant>> failuresByIp = new ConcurrentHashMap<>();
    private final AtomicInteger operations = new AtomicInteger();

    @Autowired
    public LoginAttemptLimiter(
            @Value("${app.auth.login-limit.max-failures-per-account}") int maxFailuresPerAccount,
            @Value("${app.auth.login-limit.max-failures-per-ip}") int maxFailuresPerIp,
            @Value("${app.auth.login-limit.window}") Duration window) {
        this(maxFailuresPerAccount, maxFailuresPerIp, window, Clock.systemUTC());
    }

    LoginAttemptLimiter(int maxFailuresPerAccount, int maxFailuresPerIp, Duration window, Clock clock) {
        this.maxFailuresPerAccount = maxFailuresPerAccount;
        this.maxFailuresPerIp = maxFailuresPerIp;
        this.window = window;
        this.clock = clock;
    }

    /**
     * Se llama ANTES de verificar la contrasena. Si la cuenta o la IP agotaron
     * sus intentos lanza {@link LoginThrottledException}: ni siquiera la
     * contrasena correcta entra mientras dure el bloqueo, y no se gasta un
     * calculo de bcrypt en un intento que ya se sabe rechazado.
     */
    public void checkAllowed(String clientIp, String slug, String email) {
        Instant now = clock.instant();
        Duration accountWait = waitFor(failuresByAccount, accountKey(slug, email),
                maxFailuresPerAccount, now);
        Duration ipWait = waitFor(failuresByIp, clientIp, maxFailuresPerIp, now);

        Duration wait = accountWait.compareTo(ipWait) >= 0 ? accountWait : ipWait;
        if (!wait.isZero()) {
            throw new LoginThrottledException(wait);
        }
    }

    public void recordFailure(String clientIp, String slug, String email) {
        Instant now = clock.instant();
        append(failuresByAccount, accountKey(slug, email), now);
        append(failuresByIp, clientIp, now);
        maybeCleanUp(now);
    }

    /**
     * Un acierto limpia el contador de la cuenta, pero no el de la IP: si no,
     * un atacante con una cuenta propia podria intercalar logins validos desde
     * la misma IP para reiniciar el contador mientras prueba otras cuentas.
     */
    public void recordSuccess(String slug, String email) {
        failuresByAccount.remove(accountKey(slug, email));
        maybeCleanUp(clock.instant());
    }

    /** Correo en minusculas: variar mayusculas no debe abrir un contador nuevo. */
    private static String accountKey(String slug, String email) {
        return slug.toLowerCase(Locale.ROOT) + "|" + email.strip().toLowerCase(Locale.ROOT);
    }

    /** Tiempo que falta para liberar la llave, o cero si no esta bloqueada. */
    private Duration waitFor(Map<String, Deque<Instant>> failures, String key,
                             int maxFailures, Instant now) {
        Deque<Instant> attempts = failures.get(key);
        if (attempts == null) {
            return Duration.ZERO;
        }
        synchronized (attempts) {
            prune(attempts, now);
            if (attempts.size() < maxFailures) {
                return Duration.ZERO;
            }
            // Se libera cuando el fallo que completo el limite sale de la ventana.
            Instant releasedAt = attempts.stream()
                    .skip(attempts.size() - maxFailures)
                    .findFirst()
                    .orElseThrow()
                    .plus(window);
            return Duration.between(now, releasedAt);
        }
    }

    private void append(Map<String, Deque<Instant>> failures, String key, Instant now) {
        Deque<Instant> attempts = failures.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        synchronized (attempts) {
            prune(attempts, now);
            attempts.addLast(now);
        }
    }

    private void prune(Deque<Instant> attempts, Instant now) {
        Instant cutoff = now.minus(window);
        while (!attempts.isEmpty() && !attempts.peekFirst().isAfter(cutoff)) {
            attempts.removeFirst();
        }
    }

    /** Evita que los mapas crezcan sin limite con llaves que ya no bloquean nada. */
    private void maybeCleanUp(Instant now) {
        if (operations.incrementAndGet() % CLEANUP_EVERY != 0) {
            return;
        }
        for (Map<String, Deque<Instant>> failures : List.of(failuresByAccount, failuresByIp)) {
            failures.entrySet().removeIf(entry -> {
                synchronized (entry.getValue()) {
                    prune(entry.getValue(), now);
                    return entry.getValue().isEmpty();
                }
            });
        }
    }
}
