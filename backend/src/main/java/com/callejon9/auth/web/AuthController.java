package com.callejon9.auth.web;

import com.callejon9.auth.service.AuthService;
import com.callejon9.auth.web.dto.LoginRequest;
import com.callejon9.auth.web.dto.LoginResponse;
import com.callejon9.auth.web.dto.MeResponse;
import com.callejon9.tenancy.TenantFilter;
import jakarta.validation.Valid;
import java.time.Duration;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
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
    private final long accessTokenMinutes;

    public AuthController(AuthService authService,
                          @Value("${app.jwt.access-token-minutes}") long accessTokenMinutes) {
        this.authService = authService;
        this.accessTokenMinutes = accessTokenMinutes;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        var authenticated = authService.authenticate(
                request.slug(), request.email(), request.password());

        ResponseCookie cookie = ResponseCookie.from(
                        TenantFilter.ACCESS_TOKEN_COOKIE, authenticated.accessToken())
                .httpOnly(true)
                .secure(false)          // en produccion: true, detras de HTTPS
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
                .httpOnly(true).path("/").maxAge(Duration.ZERO).build();

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
}
