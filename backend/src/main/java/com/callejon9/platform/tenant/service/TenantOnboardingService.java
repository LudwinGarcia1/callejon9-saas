package com.callejon9.platform.tenant.service;

import com.callejon9.platform.plan.domain.Plan;
import com.callejon9.platform.plan.repository.PlanRepository;
import com.callejon9.platform.subscription.domain.Subscription;
import com.callejon9.platform.subscription.domain.SubscriptionStatus;
import com.callejon9.platform.subscription.repository.SubscriptionRepository;
import com.callejon9.platform.tenant.domain.Tenant;
import com.callejon9.platform.tenant.repository.TenantRepository;
import com.callejon9.shared.error.BusinessRuleException;
import com.callejon9.tenancy.TenantContext;
import com.callejon9.user.domain.User;
import com.callejon9.user.domain.UserRole;
import com.callejon9.user.repository.UserRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Da de alta un restaurante nuevo en el SaaS: crea el tenant, su suscripcion
 * activa y el usuario administrador inicial.
 *
 * <p>Esto ocurre en DOS transacciones, no en una sola:
 * <ol>
 *   <li>tenant + suscripcion, SIN {@link TenantContext} fijado: son tablas de
 *       control plane (sin RLS), no necesitan tenant activo.</li>
 *   <li>usuario administrador, CON {@link TenantContext} ya fijado al tenant
 *       recien creado.</li>
 * </ol>
 *
 * <p>La separacion es obligatoria, no cosmetica. {@code TenantAwareTransactionManager}
 * publica {@code app.tenant_id} en la sesion de Postgres dentro de {@code doBegin()},
 * es decir, al ABRIR la transaccion. Si tenant+suscripcion y usuario compartieran
 * una sola transaccion @Transactional, fijar {@code TenantContext.set(...)} a mitad
 * de metodo no cambiaria nada en Postgres (el ThreadLocal de Java ya abrio la
 * transaccion sin tenant), y el INSERT en {@code users} violaria la politica RLS
 * {@code WITH CHECK}. Por eso aqui no se usa {@code @Transactional} declarativo:
 * cada paso abre su propia transaccion con {@code TransactionTemplate}, de modo que
 * el tenant este fijado ANTES de que la segunda transaccion se abra.
 *
 * <p><b>Ventana no atomica:</b> como consecuencia, esta operacion ya no es una
 * unidad transaccional unica. Si el alta del administrador falla despues de que
 * el tenant y la suscripcion ya se confirmaron, se compensa borrando el tenant:
 * el {@code ON DELETE CASCADE} de {@code subscriptions.tenant_id} se lleva la
 * suscripcion con el, y como {@code tenants} no tiene RLS, el borrado no depende
 * de ningun tenant activo. Se opto por compensar (en vez de dejarlo huerfano)
 * porque un tenant sin ningun usuario administrador es inservible y no debe
 * quedar visible para reintentos de signup con el mismo slug.
 */
@Service
public class TenantOnboardingService {

    private final TenantRepository tenantRepository;
    private final PlanRepository planRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TransactionTemplate transactionTemplate;

    public TenantOnboardingService(
            TenantRepository tenantRepository,
            PlanRepository planRepository,
            SubscriptionRepository subscriptionRepository,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            TransactionTemplate transactionTemplate) {
        this.tenantRepository = tenantRepository;
        this.planRepository = planRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.transactionTemplate = transactionTemplate;
    }

    public Tenant onboard(
            String restaurantName,
            String slug,
            String adminEmail,
            String adminFullName,
            String rawPassword,
            String planCode) {

        // Validaciones antes de escribir nada.
        if (tenantRepository.existsBySlug(slug)) {
            throw new BusinessRuleException(
                    "Ya existe un restaurante con el identificador '" + slug + "'.");
        }

        Plan plan = planRepository.findByCode(planCode)
                .orElseThrow(() -> new BusinessRuleException(
                        "El plan '" + planCode + "' no existe."));

        // Transaccion 1: control plane, sin tenant en contexto.
        Tenant tenant = transactionTemplate.execute(status ->
                createTenantAndSubscription(restaurantName, slug, plan));

        // Transaccion 2: escribe en `users`, gobernada por RLS. El tenant debe
        // quedar fijado ANTES de abrir esta transaccion.
        TenantContext.set(tenant.getId());
        try {
            transactionTemplate.executeWithoutResult(status ->
                    createAdminUser(adminEmail, adminFullName, rawPassword));
        } catch (RuntimeException ex) {
            tenantRepository.deleteById(tenant.getId());
            throw ex;
        } finally {
            TenantContext.clear();
        }

        return tenant;
    }

    private Tenant createTenantAndSubscription(String restaurantName, String slug, Plan plan) {
        Tenant tenant = tenantRepository.save(Tenant.builder()
                .name(restaurantName)
                .slug(slug)
                .active(true)
                .build());

        Instant now = Instant.now();
        subscriptionRepository.save(Subscription.builder()
                .tenantId(tenant.getId())
                .planId(plan.getId())
                .status(SubscriptionStatus.ACTIVE)
                .startedAt(now)
                .currentPeriodEnd(now.plus(30, ChronoUnit.DAYS))
                .build());

        return tenant;
    }

    private void createAdminUser(String adminEmail, String adminFullName, String rawPassword) {
        // User (a diferencia de Subscription) NO declara tenantId propio: lo
        // hereda de TenantScopedEntity, y como esta clase usa @Builder (no
        // @SuperBuilder), el builder no cubre campos heredados. Por eso no se
        // fija aqui: @PrePersist lo toma de TenantContext, que ya esta fijado.
        userRepository.save(User.builder()
                .email(adminEmail)
                .passwordHash(passwordEncoder.encode(rawPassword))
                .fullName(adminFullName)
                .role(UserRole.ADMIN)
                .active(true)
                .totpEnabled(false)
                .build());
    }
}
