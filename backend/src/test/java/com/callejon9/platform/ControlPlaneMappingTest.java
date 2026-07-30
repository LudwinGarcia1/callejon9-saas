package com.callejon9.platform;

import com.callejon9.platform.plan.repository.PlanRepository;
import com.callejon9.platform.tenant.domain.Tenant;
import com.callejon9.platform.tenant.repository.TenantRepository;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ControlPlaneMappingTest {

    @Autowired
    private PlanRepository planRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Test
    void seededPlansAreReadableThroughJpa() {
        var free = planRepository.findByCode("FREE").orElseThrow();

        assertThat(free.getName()).isEqualTo("Gratis");
        assertThat(free.getMaxUsers()).isEqualTo(3);
        assertThat(free.getMaxTables()).isEqualTo(5);
        assertThat(free.getPriceMonthly()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void tenantRoundTripsThroughJpaAndPopulatesAuditFields() {
        Tenant saved = tenantRepository.save(Tenant.builder()
                .name("Mapping Test")
                .slug("mapping-test")
                .active(true)
                .build());

        tenantRepository.flush();

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(tenantRepository.existsBySlug("mapping-test")).isTrue();
    }
}
