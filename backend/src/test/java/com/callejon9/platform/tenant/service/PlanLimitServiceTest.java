package com.callejon9.platform.tenant.service;

import com.callejon9.platform.plan.domain.Plan;
import com.callejon9.platform.plan.repository.PlanRepository;
import com.callejon9.platform.subscription.domain.Subscription;
import com.callejon9.platform.subscription.domain.SubscriptionStatus;
import com.callejon9.platform.subscription.repository.SubscriptionRepository;
import com.callejon9.platform.tenant.repository.TenantRepository;
import com.callejon9.shared.error.BusinessRuleException;
import com.callejon9.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Límite de usuarios del plan")
class PlanLimitServiceTest {

    private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000010");
    private static final UUID PLAN_ID = UUID.fromString("00000000-0000-0000-0000-000000000020");
    private static final int MAX_USERS = 3;

    @Mock private SubscriptionRepository subscriptionRepository;
    @Mock private PlanRepository planRepository;
    @Mock private UserRepository userRepository;
    @Mock private TenantRepository tenantRepository;

    private PlanLimitService service;

    @BeforeEach
    void setUp() {
        service = new PlanLimitService(
                subscriptionRepository, planRepository, userRepository, tenantRepository);

        Subscription subscription = Subscription.builder()
                .tenantId(TENANT_ID)
                .planId(PLAN_ID)
                .status(SubscriptionStatus.ACTIVE)
                .startedAt(Instant.parse("2026-01-01T00:00:00Z"))
                .build();
        Plan plan = Plan.builder()
                .code("FREE")
                .name("Plan gratuito")
                .priceMonthly(BigDecimal.ZERO)
                .maxUsers(MAX_USERS)
                .maxTables(5)
                .build();

        when(subscriptionRepository.findByTenantIdAndStatusIn(
                        eq(TENANT_ID), anyList()))
                .thenReturn(Optional.of(subscription));
        when(planRepository.findById(PLAN_ID)).thenReturn(Optional.of(plan));
    }

    @Test
    @DisplayName("permite el alta cuando queda un cupo")
    void allowsCreationBelowThePlanLimit() {
        when(userRepository.countByTenantId(TENANT_ID)).thenReturn(MAX_USERS - 1L);

        assertThatCode(() -> service.assertCanAddUser(TENANT_ID))
                .doesNotThrowAnyException();

        verify(tenantRepository).findByIdForUpdate(TENANT_ID);
    }

    @Test
    @DisplayName("rechaza el alta cuando el restaurante ya alcanzó el límite")
    void rejectsCreationAtThePlanLimit() {
        when(userRepository.countByTenantId(TENANT_ID)).thenReturn((long) MAX_USERS);

        assertThatThrownBy(() -> service.assertCanAddUser(TENANT_ID))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Alcanzaste el limite de 3 usuarios del plan FREE.");

        verify(tenantRepository).findByIdForUpdate(TENANT_ID);
    }

    @Test
    @DisplayName("rechaza el alta si el conteo ya superó el límite")
    void rejectsCreationAboveThePlanLimit() {
        when(userRepository.countByTenantId(TENANT_ID)).thenReturn((long) MAX_USERS + 1);

        assertThatThrownBy(() -> service.assertCanAddUser(TENANT_ID))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Alcanzaste el limite de 3 usuarios del plan FREE.");

        InOrder order = inOrder(tenantRepository, userRepository);
        order.verify(tenantRepository).findByIdForUpdate(TENANT_ID);
        order.verify(userRepository).countByTenantId(TENANT_ID);
    }
}
