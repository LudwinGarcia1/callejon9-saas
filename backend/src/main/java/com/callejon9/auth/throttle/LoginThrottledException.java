package com.callejon9.auth.throttle;

import java.time.Duration;

/** El login esta bloqueado temporalmente por demasiados intentos fallidos. */
public class LoginThrottledException extends RuntimeException {

    private final Duration retryAfter;

    public LoginThrottledException(Duration retryAfter) {
        super("Demasiados intentos de inicio de sesion.");
        this.retryAfter = retryAfter;
    }

    public Duration getRetryAfter() {
        return retryAfter;
    }
}
