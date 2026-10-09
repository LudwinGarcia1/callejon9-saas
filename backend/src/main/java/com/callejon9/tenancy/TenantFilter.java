package com.callejon9.tenancy;

import com.callejon9.auth.service.AccessTokenVerifier;
import com.callejon9.auth.service.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Traduce el JWT de la peticion a un Authentication de Spring Security y al
 * TenantContext. Es la unica pieza que decide cual es el tenant de la peticion.
 *
 * Un token solo autentica si {@link AccessTokenVerifier} confirma que su
 * sesion sigue vigente en el servidor: tras un logout, el mismo JWT deja de
 * servir aunque todavia no haya expirado.
 *
 * Siempre limpia el TenantContext en el finally: si el ThreadLocal sobreviviera
 * al final de la peticion, el siguiente uso de ese hilo del pool heredaria el
 * tenant anterior.
 */
@Component
public class TenantFilter extends OncePerRequestFilter {

    public static final String ACCESS_TOKEN_COOKIE = "access_token";

    /**
     * Atributo de peticion con la sesion ({@code sid}) del access token ya
     * verificado. Lo usa el logout para revocar esa sesion aunque el cliente
     * no envie la cookie de refresco.
     */
    public static final String SESSION_ID_ATTRIBUTE = "com.callejon9.sessionId";

    private final AccessTokenVerifier accessTokenVerifier;

    public TenantFilter(AccessTokenVerifier accessTokenVerifier) {
        this.accessTokenVerifier = accessTokenVerifier;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain chain) throws ServletException, IOException {
        try {
            readToken(request).ifPresent(token -> authenticate(token, request));
            chain.doFilter(request, response);
        } finally {
            TenantContext.clear();
            SecurityContextHolder.clearContext();
        }
    }

    /**
     * Un {@code response.sendError(...)} en un Tomcat real (no en MockMvc)
     * dispara un segundo despacho interno con DispatcherType.ERROR hacia
     * "/error", que atraviesa de nuevo toda la cadena de filtros de Spring
     * Security. OncePerRequestFilter se salta ese segundo despacho por
     * defecto, pero este filtro es quien traduce la cookie JWT en la
     * Authentication: si no corriera tambien ahi, el segundo paso veria un
     * contexto vacio (relleno luego por AnonymousAuthenticationFilter) y
     * ExceptionTranslationFilter, al ver una autenticacion anonima, llamaria
     * otra vez a sendError — esta vez con 401 — pisando el 403 correcto que
     * ya se habia calculado en el primer paso.
     */
    @Override
    protected boolean shouldNotFilterErrorDispatch() {
        return false;
    }

    private void authenticate(String token, HttpServletRequest request) {
        // Token invalido, expirado, manipulado o de una sesion revocada: la
        // peticion sigue anonima y la cadena de autorizacion la rechaza con 401.
        accessTokenVerifier.verify(token).ifPresent(claims -> publish(claims, request));
    }

    private void publish(JwtService.TokenClaims claims, HttpServletRequest request) {
        TenantContext.set(claims.tenantId());

        var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + claims.role().name()));
        var authentication = new UsernamePasswordAuthenticationToken(
                claims.userId(), null, authorities);
        authentication.setDetails(request.getRequestURI());

        SecurityContextHolder.getContext().setAuthentication(authentication);
        request.setAttribute(SESSION_ID_ATTRIBUTE, claims.sessionId());
    }

    private Optional<String> readToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        return Arrays.stream(cookies)
                .filter(cookie -> ACCESS_TOKEN_COOKIE.equals(cookie.getName()))
                .map(Cookie::getValue)
                .filter(value -> !value.isBlank())
                .findFirst();
    }
}
