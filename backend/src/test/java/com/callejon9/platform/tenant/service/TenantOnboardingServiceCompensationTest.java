package com.callejon9.platform.tenant.service;

import com.callejon9.platform.plan.domain.Plan;
import com.callejon9.platform.plan.repository.PlanRepository;
import com.callejon9.platform.subscription.domain.Subscription;
import com.callejon9.platform.subscription.repository.SubscriptionRepository;
import com.callejon9.platform.tenant.domain.Tenant;
import com.callejon9.platform.tenant.repository.TenantRepository;
import com.callejon9.user.domain.User;
import com.callejon9.user.repository.UserRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Ejercita la ruta de compensacion de {@link TenantOnboardingService} sin
 * base de datos real: TenantRepository, PlanRepository, SubscriptionRepository,
 * UserRepository, PasswordEncoder y el PlatformTransactionManager que respalda
 * el TransactionTemplate se sustituyen por mocks de Mockito, de modo que el
 * fallo del INSERT del administrador se puede forzar de forma deterministica
 * (userRepository.save lanza la excepcion) sin depender de una violacion real
 * de RLS ni de tocar el codigo de produccion para hacerlo "testeable".
 *
 * <p>El PlatformTransactionManager mockeado deja pasar getTransaction/commit/
 * rollback como no-ops: no valida semantica transaccional real, solo permite
 * que TransactionTemplate.execute()/executeWithoutResult() invoquen el
 * callback de TenantOnboardingService tal cual lo haria en produccion.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Compensacion cuando el alta del administrador falla")
class TenantOnboardingServiceCompensationTest {

    @Mock private TenantRepository tenantRepository;
    @Mock private PlanRepository planRepository;
    @Mock private SubscriptionRepository subscriptionRepository;
    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private PlatformTransactionManager transactionManager;
    @Mock private TransactionStatus transactionStatus;

    private TenantOnboardingService service;

    private void wireHappyPathUpToTheUserInsert(UUID tenantId) {
        when(transactionManager.getTransaction(any(TransactionDefinition.class)))
                .thenReturn(transactionStatus);

        when(tenantRepository.existsBySlug("onboarding-compensacion")).thenReturn(false);

        Plan plan = Plan.builder().code("FREE").name("Gratis").maxUsers(3).maxTables(5).build();
        plan.setId(UUID.randomUUID());
        when(planRepository.findByCode("FREE")).thenReturn(Optional.of(plan));

        // Simula lo que hace Hibernate al persistir: asigna el id generado.
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(invocation -> {
            Tenant tenant = invocation.getArgument(0);
            tenant.setId(tenantId);
            return tenant;
        });
        when(subscriptionRepository.save(any(Subscription.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(passwordEncoder.encode("Secreto123!")).thenReturn("hash-irrelevante");

        service = new TenantOnboardingService(
                tenantRepository, planRepository, subscriptionRepository, userRepository,
                passwordEncoder, new TransactionTemplate(transactionManager));
    }

    @Test
    @DisplayName("borra el tenant y propaga la excepcion original si el INSERT del admin falla")
    void deletesTheTenantAndPropagatesTheOriginalFailure() {
        UUID tenantId = UUID.randomUUID();
        wireHappyPathUpToTheUserInsert(tenantId);

        RuntimeException insertFailure = new RuntimeException("conexion perdida durante el INSERT");
        when(userRepository.save(any(User.class))).thenThrow(insertFailure);

        assertThatThrownBy(() -> service.onboard(
                "Con compensacion", "onboarding-compensacion",
                "admin@compensacion.com", "Admin", "Secreto123!", "FREE"))
                .isSameAs(insertFailure);

        verify(tenantRepository).deleteById(tenantId);
    }

    @Test
    @DisplayName("si la compensacion tambien falla, la adjunta como suprimida sin ocultar la causa original")
    void attachesTheCompensationFailureAsSuppressedInsteadOfDiscardingTheOriginalOne() {
        UUID tenantId = UUID.randomUUID();
        wireHappyPathUpToTheUserInsert(tenantId);

        RuntimeException insertFailure = new RuntimeException("conexion perdida durante el INSERT");
        when(userRepository.save(any(User.class))).thenThrow(insertFailure);

        RuntimeException deleteFailure = new RuntimeException("reset de conexion durante el DELETE");
        doThrow(deleteFailure).when(tenantRepository).deleteById(tenantId);

        assertThatThrownBy(() -> service.onboard(
                "Con compensacion", "onboarding-compensacion",
                "admin@compensacion.com", "Admin", "Secreto123!", "FREE"))
                .isSameAs(insertFailure)
                .satisfies(thrown -> assertThat(thrown.getSuppressed()).containsExactly(deleteFailure));
    }
}
