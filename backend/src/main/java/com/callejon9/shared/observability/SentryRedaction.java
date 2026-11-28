package com.callejon9.shared.observability;

import java.util.regex.Pattern;

/**
 * Tacha correos y JWT de los textos que salen hacia Sentry.
 *
 * <p>Sentry es un servicio externo y su copia no tiene RLS. Los SDK ya no
 * adjuntan cookies, cabeceras ni el cuerpo de la peticion, pero un mensaje de
 * log puede llevar datos dentro: por ejemplo, el detalle de una violacion de
 * unicidad de PostgreSQL incluye el valor duplicado, que puede ser un correo.
 */
public final class SentryRedaction {

    static final String EMAIL_MASK = "[correo]";
    static final String JWT_MASK = "[jwt]";

    private static final Pattern EMAIL =
            Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern JWT =
            Pattern.compile("eyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]*");

    private SentryRedaction() {
    }

    /** Devuelve el texto con correos y JWT reemplazados; {@code null} sigue siendo {@code null}. */
    public static String redact(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        String withoutTokens = JWT.matcher(text).replaceAll(JWT_MASK);
        return EMAIL.matcher(withoutTokens).replaceAll(EMAIL_MASK);
    }
}
