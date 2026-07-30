package com.callejon9.platform.tenant.service;

import com.callejon9.platform.plan.domain.Plan;
import com.callejon9.platform.plan.repository.PlanRepository;
import com.callejon9.platform.subscription.domain.SubscriptionStatus;
import com.callejon9.platform.subscription.repository.SubscriptionRepository;
import com.callejon9.shared.error.BusinessRuleException;
import com.callejon9.user.repository.UserRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Hace que los planes signifiquen algo: sin esto serian filas decorativas. */
@Service
public class PlanLimitService {

    private final SubscriptionRepository subscriptionRepository;
    private final PlanRepository planRepository;
    private final UserRepository userRepository;

    public PlanLimitService(
            SubscriptionRepository subscriptionRepository,
            PlanRepository planRepository,
            UserRepository userRepository) {
        this.subscriptionRepository = subscriptionRepository;
        this.planRepository = planRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public void assertCanAddUser(UUID tenantId) {
        Plan plan = activePlanOf(tenantId);
        long currentUsers = userRepository.countByTenantId(tenantId);

        if (currentUsers >= plan.getMaxUsers()) {
            throw new BusinessRuleException(
                    "Alcanzaste el limite de " + plan.getMaxUsers()
                            + " usuarios del plan " + plan.getCode() + ".");
        }
    }

    private Plan activePlanOf(UUID tenantId) {
        var subscription = subscriptionRepository
                .findByTenantIdAndStatusIn(tenantId,
                        List.of(SubscriptionStatus.ACTIVE, SubscriptionStatus.TRIALING))
                .orElseThrow(() -> new BusinessRuleException(
                        "El restaurante no tiene una suscripcion activa."));

        return planRepository.findById(subscription.getPlanId())
                .orElseThrow(() -> new BusinessRuleException(
                        "La suscripcion apunta a un plan inexistente."));
    }
}
