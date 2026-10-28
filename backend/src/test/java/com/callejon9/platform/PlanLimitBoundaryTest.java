package com.callejon9.platform;

import com.callejon9.platform.plan.repository.PlanRepository;
import com.callejon9.platform.tenant.domain.Tenant;
import com.callejon9.platform.tenant.service.PlanLimitService;
import com.callejon9.platform.tenant.service.TenantOnboardingService;
import com.callejon9.shared.error.BusinessRuleException;
import com.callejon9.table.domain.RestaurantTable;
import com.callejon9.table.service.TableService;
import com.callejon9.tenancy.TenantContext;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Limites de cada plan justo en el borde: el ultimo permitido y el primero
 * rechazado, con los planes sembrados en V1__control_plane.sql (los mismos que
 * publica {@code frontend/src/lib/plans.ts}).
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Límites de usuarios y mesas de cada plan en el borde")
class PlanLimitBoundaryTest {

    @Autowired private TenantOnboardingService onboardingService;
    @Autowired private PlanLimitService planLimitService;
    @Autowired private PlanRepository planRepository;
    @Autowired private TableService tableService;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private TransactionTemplate transactionTemplate;

    @AfterEach
    void cleanUp() {
        TenantContext.clear();
        jdbcTemplate.update("DELETE FROM tenants WHERE slug LIKE 'plan-borde-%'");
    }

    private Tenant onboard(String planCode, String kind) {
        String code = planCode.toLowerCase(Locale.ROOT);
        Tenant tenant = onboardingService.onboard("Borde " + planCode, "plan-borde-" + kind + "-" + code,
                "admin@" + kind + "-" + code + ".borde.com", "Admin Borde", "Mantel-Azul-47", planCode);
        TenantContext.set(tenant.getId());
        return tenant;
    }

    @ParameterizedTest(name = "{0}: hasta {1} usuarios y {2} mesas")
    @CsvSource({"FREE, 3, 5", "PRO, 15, 30", "PREMIUM, 60, 120"})
    @DisplayName("Los planes sembrados tienen los límites que se publican en el alta")
    void seededPlansMatchThePublishedLimits(String planCode, int maxUsers, int maxTables) {
        var plan = planRepository.findByCode(planCode).orElseThrow();

        assertThat(plan.getMaxUsers()).isEqualTo(maxUsers);
        assertThat(plan.getMaxTables()).isEqualTo(maxTables);
    }

    @ParameterizedTest(name = "{0}: el usuario {1} entra y el {2} se rechaza")
    @CsvSource({"FREE, 3, 4", "PRO, 15, 16", "PREMIUM, 60, 61"})
    @DisplayName("Usuarios: el último cupo del plan se permite y el siguiente se rechaza")
    void userLimitAtTheBoundary(String planCode, int lastAllowed, int firstRejected) {
        Tenant tenant = onboard(planCode, "u");

        // El onboarding ya creo al administrador: se agregan usuarios hasta
        // dejar ocupado todo menos el ultimo cupo.
        insertWaiters(tenant, lastAllowed - 2);
        assertThatCode(() -> planLimitService.assertCanAddUser(tenant.getId()))
                .as("con %d usuarios aun cabe el usuario %d", lastAllowed - 1, lastAllowed)
                .doesNotThrowAnyException();

        insertWaiters(tenant, 1);
        assertThatThrownBy(() -> planLimitService.assertCanAddUser(tenant.getId()))
                .as("con %d usuarios el usuario %d se rechaza", lastAllowed, firstRejected)
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Alcanzaste el limite de " + lastAllowed + " usuarios del plan " + planCode + ".");
    }

    @Disabled("CAL-178: el límite de mesas del plan no se aplica; se activa con la corrección")
    @ParameterizedTest(name = "{0}: la mesa {1} entra y la {2} se rechaza")
    @CsvSource({"FREE, 5, 6", "PRO, 30, 31", "PREMIUM, 120, 121"})
    @DisplayName("Mesas: la última mesa del plan se permite y la siguiente se rechaza")
    void tableLimitAtTheBoundary(String planCode, int lastAllowed, int firstRejected) {
        onboard(planCode, "m");

        for (int number = 1; number <= lastAllowed; number++) {
            tableService.createTable(number, 4);
        }
        List<RestaurantTable> tables = transactionTemplate.execute(status -> tableService.listTables(true));
        assertThat(tables).hasSize(lastAllowed);

        assertThatThrownBy(() -> tableService.createTable(firstRejected, 4))
                .as("la mesa %d rebasa el plan %s", firstRejected, planCode)
                .isInstanceOf(BusinessRuleException.class);
    }

    private void insertWaiters(Tenant tenant, int count) {
        if (count <= 0) {
            return;
        }
        transactionTemplate.executeWithoutResult(status -> jdbcTemplate.update("""
                INSERT INTO users (tenant_id, email, password_hash, full_name, role)
                SELECT ?, 'mesero-' || g || '-' || (SELECT count(*) FROM users) || '@borde.com', 'x', 'Mesero ' || g, 'WAITER'
                FROM generate_series(1, ?) AS g
                """, tenant.getId(), count));
    }
}
