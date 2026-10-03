package com.callejon9.auth.web;

import com.callejon9.auth.service.AuthService;
import com.callejon9.auth.throttle.ClientIp;
import com.callejon9.auth.throttle.LoginAttemptLimiter;
import com.callejon9.auth.throttle.LoginThrottledException;
import com.callejon9.auth.web.dto.LoginRequest;
import com.callejon9.auth.web.dto.LoginResponse;
import com.callejon9.auth.web.dto.MeResponse;
import com.callejon9.tenancy.TenantFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Duration;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final LoginAttemptLimiter loginAttemptLimiter;
    private final long accessTokenMinutes;
    private final boolean secureCookie;

    /**
     * {@code secureCookie} decide si la cookie del token lleva el atributo
     * Secure, que impide al navegador enviarla por HTTP plano. Se activa con
     * AUTH_SECURE_COOKIE=true en cualquier entorno servido por HTTPS; en local
     * queda apagado porque el backend corre sobre http://localhost.
     */
    public AuthController(AuthService authService,
                          LoginAttemptLimiter loginAttemptLimiter,
                          @Value("${app.jwt.access-token-minutes}") long accessTokenMinutes,
                          @Value("${app.auth.secure-cookie}") boolean secureCookie) {
        this.authService = authService;
        this.loginAttemptLimiter = loginAttemptLimiter;
        this.accessTokenMinutes = accessTokenMinutes;
        this.secureCookie = secureCookie;
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

        ResponseCookie cookie = ResponseCookie.from(
                        TenantFilter.ACCESS_TOKEN_COOKIE, authenticated.accessToken())
                .httpOnly(true)
                .secure(secureCookie)
                .sameSite("Strict")
                .path("/")
                .maxAge(Duration.ofMinutes(accessTokenMinutes))
                .build();

        var user = authenticated.user();
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(new LoginResponse(user.getId(), user.getFullName(),
                        user.getRole().name(), false));
    }

    /**
     * Recupera la identidad del usuario autenticado a partir de la cookie
     * httpOnly. Es la unica forma que tiene el frontend de saber quien esta
     * conectado tras refrescar la pagina, ya que JavaScript no puede leer una
     * cookie httpOnly.
     *
     * <p>La ruta cae bajo la regla permitAll de "/api/v1/auth/**", asi que un
     * llamado anonimo SI llega hasta aqui (con una autenticacion anonima, no
     * nula). Por eso el rechazo es explicito: {@link TenantFilter} solo fija
     * un {@link UUID} como principal cuando el token es valido, asi que
     * cualquier otro tipo de principal (el "anonymousUser" de Spring
     * Security) se trata como no autenticado.
     */
    @GetMapping("/me")
    public ResponseEntity<MeResponse> me(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof UUID userId)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        var current = authService.currentUser(userId);
        return ResponseEntity.ok(new MeResponse(
                current.user().getId(),
                current.user().getFullName(),
                current.user().getRole().name(),
                current.tenant().getId(),
                current.tenant().getSlug(),
                current.tenant().getName()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        ResponseCookie cleared = ResponseCookie.from(TenantFilter.ACCESS_TOKEN_COOKIE, "")
                .httpOnly(true)
                .secure(secureCookie)
                .sameSite("Strict")
                .path("/")
                .maxAge(Duration.ZERO)
                .build();

        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cleared.toString())
                .build();
    }

    @ExceptionHandler(BadCredentialsException.class)
    ResponseEntity<Void> onBadCredentials() {
        // Mensaje deliberadamente vacio: no se revela si fallo el correo, la
        // contrasena o el restaurante.
        return ResponseEntity.status(401).build();
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
