package com.callejon9.platform.tenant.service;

import com.callejon9.platform.plan.domain.Plan;
import com.callejon9.platform.plan.repository.PlanRepository;
import com.callejon9.platform.subscription.domain.Subscription;
import com.callejon9.platform.subscription.domain.SubscriptionStatus;
import com.callejon9.platform.subscription.repository.SubscriptionRepository;
import com.callejon9.platform.tenant.repository.TenantRepository;
import com.callejon9.shared.error.BusinessRuleException;
import com.callejon9.table.repository.RestaurantTableRepository;
import com.callejon9.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("UT · Límites de usuarios y mesas por plan")
class PlanLimitServicePlanBoundaryTest {

    private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000010");
    private static final UUID PLAN_ID = UUID.fromString("00000000-0000-0000-0000-000000000020");

    @Mock private SubscriptionRepository subscriptionRepository;
    @Mock private PlanRepository planRepository;
    @Mock private UserRepository userRepository;
    @Mock private TenantRepository tenantRepository;
    @Mock private RestaurantTableRepository tableRepository;

    private PlanLimitService service;

    @BeforeEach
    void setUp() {
        service = new PlanLimitService(
                subscriptionRepository, planRepository, userRepository, tenantRepository,
                tableRepository);
    }

    @ParameterizedTest(name = "{0}: permite el último usuario disponible ({1} máximo)")
    @CsvSource({"FREE,3,5", "PRO,15,30", "PREMIUM,60,120"})
    void allowsLastAvailableUserForEachPlan(String planCode, int maxUsers, int maxTables) {
        stubActivePlan(planCode, maxUsers, maxTables);
        when(userRepository.countByTenantId(TENANT_ID)).thenReturn((long) maxUsers - 1);

        assertThatCode(() -> service.assertCanAddUser(TENANT_ID))
                .doesNotThrowAnyException();

        verify(tenantRepository).findByIdForUpdate(TENANT_ID);
        verify(userRepository).countByTenantId(TENANT_ID);
    }

    @ParameterizedTest(name = "{0}: rechaza el primer usuario fuera del máximo ({1})")
    @CsvSource({"FREE,3,5", "PRO,15,30", "PREMIUM,60,120"})
    void rejectsFirstUserAboveEachPlanLimit(String planCode, int maxUsers, int maxTables) {
        stubActivePlan(planCode, maxUsers, maxTables);
        when(userRepository.countByTenantId(TENANT_ID)).thenReturn((long) maxUsers);

        assertThatThrownBy(() -> service.assertCanAddUser(TENANT_ID))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Alcanzaste el limite de " + maxUsers
                        + " usuarios del plan " + planCode + ".");

        verify(tenantRepository).findByIdForUpdate(TENANT_ID);
        verify(userRepository).countByTenantId(TENANT_ID);
    }

    @ParameterizedTest(name = "{0}: permite la última mesa disponible ({2} máximo)")
    @CsvSource({"FREE,3,5", "PRO,15,30", "PREMIUM,60,120"})
    void allowsLastAvailableTableForEachPlan(String planCode, int maxUsers, int maxTables) {
        stubActivePlan(planCode, maxUsers, maxTables);
        when(tableRepository.countByActiveTrue()).thenReturn((long) maxTables - 1);

        assertThatCode(() -> service.assertCanAddTable(TENANT_ID))
                .doesNotThrowAnyException();

        verify(tenantRepository).findByIdForUpdate(TENANT_ID);
        verify(tableRepository).countByActiveTrue();
    }

    @ParameterizedTest(name = "{0}: rechaza la primera mesa fuera del máximo ({2})")
    @CsvSource({"FREE,3,5", "PRO,15,30", "PREMIUM,60,120"})
    void rejectsFirstTableAboveEachPlanLimit(String planCode, int maxUsers, int maxTables) {
        stubActivePlan(planCode, maxUsers, maxTables);
        when(tableRepository.countByActiveTrue()).thenReturn((long) maxTables);

        assertThatThrownBy(() -> service.assertCanAddTable(TENANT_ID))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Alcanzaste el limite de " + maxTables
                        + " mesas del plan " + planCode + ".");

        verify(tenantRepository).findByIdForUpdate(TENANT_ID);
        verify(tableRepository).countByActiveTrue();
    }

    private void stubActivePlan(String code, int maxUsers, int maxTables) {
        Subscription subscription = Subscription.builder()
                .tenantId(TENANT_ID)
                .planId(PLAN_ID)
                .status(SubscriptionStatus.ACTIVE)
                .startedAt(Instant.parse("2026-01-01T00:00:00Z"))
                .build();
        Plan plan = Plan.builder()
                .code(code)
                .name(code)
                .priceMonthly(BigDecimal.ZERO)
                .maxUsers(maxUsers)
                .maxTables(maxTables)
                .build();

        when(subscriptionRepository.findByTenantIdAndStatusIn(eq(TENANT_ID), anyList()))
                .thenReturn(Optional.of(subscription));
        when(planRepository.findById(PLAN_ID)).thenReturn(Optional.of(plan));
    }
}
