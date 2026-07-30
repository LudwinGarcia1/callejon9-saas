package com.callejon9.auth.service;

import com.callejon9.platform.tenant.domain.Tenant;
import com.callejon9.platform.tenant.repository.TenantRepository;
import com.callejon9.tenancy.TenantContext;
import com.callejon9.user.domain.User;
import com.callejon9.user.repository.UserRepository;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class AuthService {

    /** Resultado de una autenticacion exitosa. */
    public record AuthenticatedUser(User user, String accessToken) {
    }

    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final TransactionTemplate transactionTemplate;

    public AuthService(
            TenantRepository tenantRepository,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            TransactionTemplate transactionTemplate) {
        this.tenantRepository = tenantRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.transactionTemplate = transactionTemplate;
    }

    /**
     * Resuelve el tenant por slug ANTES de buscar al usuario. Ese orden es lo que
     * impide que un correo valido de otro restaurante sirva para entrar.
     *
     * <p>La busqueda del usuario se abre con {@link TransactionTemplate} en vez de
     * {@code @Transactional}, siguiendo el mismo patron que {@code
     * TenantOnboardingService}: {@code TenantAwareTransactionManager} publica
     * {@code app.tenant_id} al ABRIR la transaccion ({@code doBegin}), asi que el
     * tenant debe quedar fijado en {@link TenantContext} antes de abrirla. Un
     * {@code @Transactional} en un metodo de esta misma clase, llamado desde
     * {@code authenticate}, tampoco serviria: el proxy de Spring no intercepta
     * las llamadas de un objeto a si mismo (self-invocation), asi que la
     * anotacion se ignoraria en silencio.
     */
    public AuthenticatedUser authenticate(String slug, String email, String rawPassword) {
        Tenant tenant = findActiveTenant(slug);

        TenantContext.set(tenant.getId());
        try {
            User user = transactionTemplate.execute(status -> loadUser(email));

            if (!user.isActive() || !passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
                throw new BadCredentialsException("Credenciales invalidas.");
            }

            return new AuthenticatedUser(user, jwtService.generateAccessToken(user));
        } finally {
            TenantContext.clear();
        }
    }

    private User loadUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new BadCredentialsException("Credenciales invalidas."));
    }

    private Tenant findActiveTenant(String slug) {
        return tenantRepository.findBySlug(slug)
                .filter(Tenant::isActive)
                .orElseThrow(() -> new BadCredentialsException("Credenciales invalidas."));
    }
}
