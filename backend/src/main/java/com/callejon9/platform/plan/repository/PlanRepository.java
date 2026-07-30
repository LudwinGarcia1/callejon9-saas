package com.callejon9.platform.plan.repository;

import com.callejon9.platform.plan.domain.Plan;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanRepository extends JpaRepository<Plan, UUID> {

    Optional<Plan> findByCode(String code);
}
