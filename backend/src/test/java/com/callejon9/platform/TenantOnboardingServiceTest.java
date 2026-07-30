package com.callejon9.platform;

import com.callejon9.platform.subscription.domain.SubscriptionStatus;
import com.callejon9.platform.subscription.repository.SubscriptionRepository;
import com.callejon9.platform.tenant.domain.Tenant;
import com.callejon9.platform.tenant.service.PlanLimitService;
import com.callejon9.platform.tenant.service.TenantOnboardingService;
import com.callejon9.shared.error.BusinessRuleException;
import com.callejon9.tenancy.TenantContext;
import com.callejon9.user.domain.UserRole;
import com.callejon9.user.repository.UserRepository;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Onboarding de un restaurante nuevo")
class TenantOnboardingServiceTest {

    @Autowired private TenantOnboardingService onboardingService;
    @Autowired private PlanLimitService planLimitService;
    @Autowired private SubscriptionRepository subscriptionRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private TransactionTemplate transactionTemplate;

    @AfterEach
    void cleanUp() {
        TenantContext.clear();
        jdbcTemplate.update("DELETE FROM tenants WHERE slug LIKE 'onboarding-%'");
    }

    @Test
    void createsTenantAdminUserAndActiveSubscription() {
        Tenant tenant = onboardingService.onboard(
                "Restaurante Onboarding", "onboarding-uno",
                "admin@onboarding.com", "Admin Uno", "Secreto123!", "FREE");

        assertThat(tenant.getId()).isNotNull();
        assertThat(tenant.isActive()).isTrue();

        var subscription = subscriptionRepository
                .findByTenantIdAndStatusIn(tenant.getId(), List.of(SubscriptionStatus.ACTIVE))
                .orElseThrow();
        assertThat(subscription.getPlanId()).isNotNull();

        TenantContext.set(tenant.getId());
        var admin = transactionTemplate.execute(status ->
                userRepository.findByEmail("admin@onboarding.com").orElseThrow());

        assertThat(admin.getRole()).isEqualTo(UserRole.ADMIN);
        assertThat(admin.getTenantId()).isEqualTo(tenant.getId());
        assertThat(passwordEncoder.matches("Secreto123!", admin.getPasswordHash())).isTrue();
    }

    @Test
    void rejectsADuplicateSlug() {
        onboardingService.onboard("Primero", "onboarding-dup",
                "a@onboarding.com", "A", "Secreto123!", "FREE");

        assertThatThrownBy(() -> onboardingService.onboard("Segundo", "onboarding-dup",
                "b@onboarding.com", "B", "Secreto123!", "FREE"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("onboarding-dup");
    }

    @Test
    void rejectsAnUnknownPlanCode() {
        assertThatThrownBy(() -> onboardingService.onboard("Sin plan", "onboarding-noplan",
                "c@onboarding.com", "C", "Secreto123!", "PLAN_INEXISTENTE"))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    @DisplayName("el plan FREE topa en 3 usuarios")
    void enforcesTheUserLimitOfThePlan() {
        Tenant tenant = onboardingService.onboard(
                "Con limite", "onboarding-limite",
                "admin@limite.com", "Admin", "Secreto123!", "FREE");

        TenantContext.set(tenant.getId());
        // El onboarding ya creo 1 usuario; el plan FREE permite 3.
        transactionTemplate.executeWithoutResult(status -> {
            planLimitService.assertCanAddUser(tenant.getId());
            jdbcTemplate.update("""
                    INSERT INTO users (tenant_id, email, password_hash, full_name, role)
                    VALUES (?, 'u2@limite.com', 'x', 'U2', 'WAITER'),
                           (?, 'u3@limite.com', 'x', 'U3', 'WAITER')
                    """, tenant.getId(), tenant.getId());
        });

        assertThatThrownBy(() -> planLimitService.assertCanAddUser(tenant.getId()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("limite");
    }
}
