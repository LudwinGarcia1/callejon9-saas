package com.callejon9.platform.plan.web;

import com.callejon9.platform.plan.domain.Plan;
import com.callejon9.platform.plan.repository.PlanRepository;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Solo SUPER_ADMIN: la regla vive en SecurityConfig, ruta /api/v1/platform/**. */
@RestController
@RequestMapping("/api/v1/platform/plans")
public class PlanController {

    public record PlanView(String code, String name, BigDecimal priceMonthly,
                           int maxUsers, int maxTables) {
    }

    private final PlanRepository planRepository;

    public PlanController(PlanRepository planRepository) {
        this.planRepository = planRepository;
    }

    @GetMapping
    public List<PlanView> list() {
        return planRepository.findAll().stream()
                .map(this::toView)
                .toList();
    }

    private PlanView toView(Plan plan) {
        return new PlanView(plan.getCode(), plan.getName(), plan.getPriceMonthly(),
                plan.getMaxUsers(), plan.getMaxTables());
    }
}
