package com.callejon9.auth.service;

import com.callejon9.platform.tenant.domain.Tenant;
import com.callejon9.platform.tenant.repository.TenantRepository;
import com.callejon9.shared.error.ResourceNotFoundException;
import com.callejon9.tenancy.TenantContext;
import com.callejon9.user.domain.User;
import com.callejon9.user.repository.UserRepository;
import java.util.UUID;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class AuthService {

    /** Resultado de una autenticacion exitosa. */
    public record AuthenticatedUser(User user, String accessToken) {
    }

    /** Identidad resuelta para GET /me: el usuario autenticado y su tenant. */
    public record CurrentUser(User user, Tenant tenant) {
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

    /**
     * Resuelve al usuario autenticado y su tenant para GET /me.
     *
     * <p>A diferencia de {@link #authenticate}, aqui si alcanza con
     * {@code @Transactional} declarativo: {@link com.callejon9.tenancy.TenantFilter}
     * ya fijo el {@link TenantContext} antes de que la peticion llegara al
     * controller, es decir, antes de que esta transaccion se abra (regla que
     * {@code TenantAwareTransactionManager} exige en su {@code doBegin}).
     */
    @Transactional(readOnly = true)
    public CurrentUser currentUser(UUID userId) {
        UUID tenantId = TenantContext.require();

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("El usuario no existe."));
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("El restaurante no existe."));

        return new CurrentUser(user, tenant);
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
