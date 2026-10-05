package com.callejon9.config;

import com.callejon9.tenancy.TenantFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final TenantFilter tenantFilter;

    public SecurityConfig(TenantFilter tenantFilter) {
        this.tenantFilter = tenantFilter;
    }

    /** BCrypt acepta los prefijos $2a$, $2b$ y $2y$, por lo que los hashes
     *  generados por la libreria bcrypt de Python son validos sin conversion. */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                // La API es stateless y se autentica con una cookie httpOnly de
                // SameSite=Strict, que ya impide el envio cross-site.
                .csrf(csrf -> csrf.disable())
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'none'; script-src 'self'; style-src 'self' 'unsafe-inline'; "
                                + "img-src 'self' data:; font-src 'self'; connect-src 'self'; "
                                + "object-src 'none'; base-uri 'self'; form-action 'self'; frame-ancestors 'none'"))
                        .frameOptions(frame -> frame.deny())
                        .contentTypeOptions(contentType -> {})
                        .referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                        // Spring solo emite HSTS cuando la peticion es segura (HTTPS).
                        .httpStrictTransportSecurity(hsts -> hsts
                                .maxAgeInSeconds(31536000).includeSubDomains(false).preload(false)))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(e -> e.authenticationEntryPoint(
                        new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                // Lista publica cerrada: todo lo demas exige sesion, y cada
                // controller declara con @PreAuthorize que roles lo usan.
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/auth/login", "/api/v1/signup").permitAll()
                        .requestMatchers("/actuator/health", "/v3/api-docs/**",
                                "/swagger-ui.html", "/swagger-ui/**")
                            .permitAll()
                        .requestMatchers("/api/v1/platform/**").hasRole("SUPER_ADMIN")
                        .anyRequest().authenticated())
                .addFilterBefore(tenantFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
