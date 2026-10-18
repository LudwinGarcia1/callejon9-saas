package com.callejon9.shared.throttle;

import java.time.Duration;

/**
 * Una operacion se rechazo por haber agotado su cupo. Se traduce a 429 con
 * {@code Retry-After} en {@code GlobalExceptionHandler}. El titulo y el
 * mensaje son fijos para cada uso: nunca repiten datos de la peticion.
 */
public class RateLimitExceededException extends RuntimeException {

    private final String title;
    private final Duration retryAfter;

    public RateLimitExceededException(String title, String message, Duration retryAfter) {
        super(message);
        this.title = title;
        this.retryAfter = retryAfter;
    }

    public String getTitle() {
        return title;
    }

    public Duration getRetryAfter() {
        return retryAfter;
    }
}
