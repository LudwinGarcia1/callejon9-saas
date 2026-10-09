package com.callejon9.auth.web;

import com.callejon9.auth.service.AuthService;
import com.callejon9.auth.service.RefreshTokenService;
import com.callejon9.auth.throttle.ClientIp;
import com.callejon9.auth.throttle.LoginAttemptLimiter;
import com.callejon9.auth.throttle.LoginThrottledException;
import com.callejon9.auth.web.dto.LoginRequest;
import com.callejon9.auth.web.dto.LoginResponse;
import com.callejon9.auth.web.dto.MeResponse;
import com.callejon9.tenancy.TenantFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final LoginAttemptLimiter loginAttemptLimiter;
    private final RefreshTokenService refreshTokenService;
    private final AuthCookies authCookies;

    /**
     * Los atributos de las cookies (httpOnly, Secure, SameSite, path y
     * duracion) viven en {@link AuthCookies}, compartidos por login, refresh
     * y logout.
     */
    public AuthController(AuthService authService,
                          LoginAttemptLimiter loginAttemptLimiter,
                          RefreshTokenService refreshTokenService,
                          AuthCookies authCookies) {
        this.authService = authService;
        this.loginAttemptLimiter = loginAttemptLimiter;
        this.refreshTokenService = refreshTokenService;
        this.authCookies = authCookies;
    }

    /**
     * El limite de intentos se revisa antes de autenticar y solo cuentan los
     * 401: un 400 por formato invalido nunca llega a probar una contrasena.
     */
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request,
                                               HttpServletRequest httpRequest) {
        String clientIp = ClientIp.of(httpRequest);
        loginAttemptLimiter.checkAllowed(clientIp, request.slug(), request.email());

        AuthService.AuthenticatedUser authenticated;
        try {
            authenticated = authService.authenticate(
                    request.slug(), request.email(), request.password());
        } catch (BadCredentialsException exception) {
            loginAttemptLimiter.recordFailure(clientIp, request.slug(), request.email());
            throw exception;
        }
        loginAttemptLimiter.recordSuccess(request.slug(), request.email());

        var user = authenticated.user();
        return ResponseEntity.ok()
                .headers(setCookies(authCookies.issue(authenticated.accessToken(),
                        authenticated.refreshToken(), authenticated.sessionExpiresAt())))
                .body(new LoginResponse(user.getId(), user.getFullName(),
                        user.getRole().name(), false));
    }

    /**
     * Rota el refresh token: el presentado queda consumido y se emite un par
     * nuevo en cookies. No requiere un access token vigente, que es justo el
     * caso para el que existe. Cualquier rechazo responde 401 sin cuerpo y
     * borra ambas cookies, para que el cliente no vuelva a intentarlo.
     *
     * <p>El rechazo se atrapa aqui y no en {@link #onBadCredentials}: ese
     * manejador tambien atiende a /login, y un login fallido no debe cerrar
     * la sesion que el navegador ya tenia abierta.
     */
    @PostMapping("/refresh")
    public ResponseEntity<Void> refresh(
            @CookieValue(name = AuthCookies.REFRESH_TOKEN_COOKIE, required = false) String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return unauthorizedClearingCookies();
        }

        RefreshTokenService.IssuedTokens issued;
        try {
            issued = refreshTokenService.rotate(refreshToken);
        } catch (BadCredentialsException rejected) {
            return unauthorizedClearingCookies();
        }
        return ResponseEntity.noContent()
                .headers(setCookies(authCookies.issue(issued.accessToken(),
                        issued.refreshToken(), issued.sessionExpiresAt())))
                .build();
    }

    /**
     * Recupera la identidad del usuario autenticado a partir de la cookie
     * httpOnly. Es la unica forma que tiene el frontend de saber quien esta
     * conectado tras refrescar la pagina, ya que JavaScript no puede leer una
     * cookie httpOnly. Sin sesion valida, SecurityConfig responde 401 antes
     * de llegar aqui.
     */
    @GetMapping("/me")
    public ResponseEntity<MeResponse> me(Authentication authentication) {
        var current = authService.currentUser((UUID) authentication.getPrincipal());
        return ResponseEntity.ok(new MeResponse(
                current.user().getId(),
                current.user().getFullName(),
                current.user().getRole().name(),
                current.tenant().getId(),
                current.tenant().getSlug(),
                current.tenant().getName()));
    }

    /**
     * Revoca la sesion en el servidor y borra las cookies.
     *
     * <p>Se revoca por las dos vias que el cliente pueda presentar: la familia
     * del refresh token y la sesion ({@code sid}) del access token que
     * {@link TenantFilter} ya verifico. Un cliente que solo envia
     * {@code access_token} (Swagger UI, una app movil) tambien cierra su sesion
     * de verdad, en vez de recibir un 204 con el token todavia valido.
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = AuthCookies.REFRESH_TOKEN_COOKIE, required = false) String refreshToken,
            @RequestAttribute(name = TenantFilter.SESSION_ID_ATTRIBUTE, required = false) UUID sessionId) {
        if (refreshToken != null && !refreshToken.isBlank()) {
            refreshTokenService.revokeSession(refreshToken);
        }
        if (sessionId != null) {
            refreshTokenService.revokeSessionById(sessionId);
        }

        return ResponseEntity.noContent()
                .headers(setCookies(authCookies.clear()))
                .build();
    }

    @ExceptionHandler(BadCredentialsException.class)
    ResponseEntity<Void> onBadCredentials() {
        // Mensaje deliberadamente vacio: no se revela si fallo el correo, la
        // contrasena o el restaurante. Tampoco toca las cookies.
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }

    private ResponseEntity<Void> unauthorizedClearingCookies() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .headers(setCookies(authCookies.clear()))
                .build();
    }

    private static HttpHeaders setCookies(List<ResponseCookie> cookies) {
        HttpHeaders headers = new HttpHeaders();
        cookies.forEach(cookie -> headers.add(HttpHeaders.SET_COOKIE, cookie.toString()));
        return headers;
    }

    /**
     * 429 con {@code Retry-After} en segundos. El mensaje es el mismo para
     * una cuenta que existe y para una que no, y no dice si el bloqueo es por
     * cuenta o por IP.
     */
    @ExceptionHandler(LoginThrottledException.class)
    ResponseEntity<ProblemDetail> onThrottled(LoginThrottledException exception) {
        long seconds = Math.max(1, (exception.getRetryAfter().toMillis() + 999) / 1000);
        long minutes = (seconds + 59) / 60;

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS,
                "Demasiados intentos fallidos. Intenta de nuevo en "
                        + (minutes == 1 ? "1 minuto." : minutes + " minutos."));
        problem.setTitle("Demasiados intentos");

        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, Long.toString(seconds))
                .body(problem);
    }
}
