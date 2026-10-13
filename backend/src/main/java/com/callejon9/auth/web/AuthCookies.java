package com.callejon9.auth.web;

import com.callejon9.tenancy.TenantFilter;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * Construye las cookies de sesion con los mismos atributos en login, refresh
 * y logout. Un navegador solo sobrescribe o borra una cookie si nombre, path
 * y dominio coinciden, asi que emitirlas desde un unico lugar evita dejar
 * copias huerfanas.
 */
@Component
public class AuthCookies {

    public static final String REFRESH_TOKEN_COOKIE = "refresh_token";

    /**
     * El refresh token solo lo necesitan /auth/refresh y /auth/logout. Con
     * este path el navegador no lo adjunta a ninguna otra peticion de la API.
     */
    static final String REFRESH_TOKEN_PATH = "/api/v1/auth";

    private final boolean secure;

    public AuthCookies(@Value("${app.auth.cookie-secure}") boolean secure) {
        this.secure = secure;
    }

    /**
     * La cookie de acceso vive lo mismo que la sesion, no los 15 minutos del
     * JWT que transporta. El middleware del frontend solo comprueba su
     * presencia para dejar pasar a las paginas protegidas; si el navegador la
     * borrara al expirar el JWT, cada navegacion rebotaria a /login aunque la
     * sesion siguiera siendo renovable. La expiracion real del JWT la sigue
     * validando {@link TenantFilter} en cada peticion.
     */
    public List<ResponseCookie> issue(String accessToken, String refreshToken, Instant sessionExpiresAt) {
        Duration maxAge = Duration.between(Instant.now(), sessionExpiresAt);
        if (maxAge.isNegative()) {
            maxAge = Duration.ZERO;
        }
        return List.of(
                cookie(TenantFilter.ACCESS_TOKEN_COOKIE, accessToken, "/", maxAge),
                cookie(REFRESH_TOKEN_COOKIE, refreshToken, REFRESH_TOKEN_PATH, maxAge));
    }

    public List<ResponseCookie> clear() {
        return List.of(
                cookie(TenantFilter.ACCESS_TOKEN_COOKIE, "", "/", Duration.ZERO),
                cookie(REFRESH_TOKEN_COOKIE, "", REFRESH_TOKEN_PATH, Duration.ZERO));
    }

    private ResponseCookie cookie(String name, String value, String path, Duration maxAge) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Strict")
                .path(path)
                .maxAge(maxAge)
                .build();
    }
}
