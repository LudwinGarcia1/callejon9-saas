package com.callejon9.platform.subscription.repository;

import com.callejon9.platform.subscription.domain.Subscription;
import com.callejon9.platform.subscription.domain.SubscriptionStatus;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {

    Optional<Subscription> findByTenantIdAndStatusIn(
            UUID tenantId, Collection<SubscriptionStatus> statuses);
}
