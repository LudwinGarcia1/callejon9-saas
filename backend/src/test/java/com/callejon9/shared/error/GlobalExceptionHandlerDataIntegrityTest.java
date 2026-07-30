package com.callejon9.shared.error;

import com.callejon9.platform.tenant.domain.Tenant;
import com.callejon9.platform.tenant.service.TenantOnboardingService;
import com.callejon9.table.domain.RestaurantTable;
import com.callejon9.table.domain.TableStatus;
import com.callejon9.table.repository.RestaurantTableRepository;
import com.callejon9.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ProblemDetail;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * Dos inserciones identicas que chocan con una restriccion UNIQUE real deben
 * dar 409, no un 500 opaco, y el detalle nunca debe repetir el mensaje de
 * Postgres (que nombra la restriccion, la columna y la tabla).
 *
 * <p>La coleccion se fuerza a nivel de repositorio (no via un endpoint HTTP)
 * porque todos los servicios de creacion de este proyecto (categorias, mesas)
 * ya hacen su propia verificacion previa (existsByX) antes de guardar; sin
 * concurrencia real, esa verificacion siempre intercepta el duplicado antes
 * de que llegue a la base de datos. Forzar la insercion doble directo contra
 * el repositorio produce la MISMA excepcion real que lanzaria Hibernate en
 * ese escenario, sin necesitar una carrera de hilos no determinista para un
 * caso que no la exige.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("GlobalExceptionHandler: violaciones de integridad de datos")
class GlobalExceptionHandlerDataIntegrityTest {

    @Autowired private TenantOnboardingService onboardingService;
    @Autowired private RestaurantTableRepository tableRepository;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private GlobalExceptionHandler exceptionHandler;

    private Tenant tenant;

    @BeforeEach
    void seed() {
        tenant = onboardingService.onboard("Conflicto Test", "conflicto-test",
                "admin@conflicto.com", "Admin", "Secreto123!", "FREE");
    }

    @AfterEach
    void cleanUp() {
        TenantContext.clear();
        jdbcTemplate.update("DELETE FROM tenants WHERE slug = 'conflicto-test'");
    }

    @Test
    @DisplayName("dos inserciones identicas que chocan con un UNIQUE real dan 409, "
            + "sin exponer el mensaje de Postgres")
    void twoIdenticalInsertsProduceAConflictNotAServerError() {
        TenantContext.set(tenant.getId());
        try {
            transactionTemplate.executeWithoutResult(status ->
                    tableRepository.saveAndFlush(newTable(7)));

            DataIntegrityViolationException collision = catchThrowableOfType(
                    () -> transactionTemplate.executeWithoutResult(status ->
                            tableRepository.saveAndFlush(newTable(7))),
                    DataIntegrityViolationException.class);

            assertThat(collision)
                    .as("la segunda insercion con el mismo (tenant_id, number) debe violar el UNIQUE real")
                    .isNotNull();

            ProblemDetail problem = exceptionHandler.onDataIntegrityViolation(collision);

            assertThat(problem.getStatus()).isEqualTo(409);
            assertThat(problem.getTitle()).isEqualTo("Conflicto de datos");
            assertThat(problem.getDetail())
                    .doesNotContainIgnoringCase("constraint")
                    .doesNotContainIgnoringCase("restaurant_tables")
                    .doesNotContainIgnoringCase("duplicate key")
                    .isEqualTo("Ya existe un registro con estos datos o se produjo un "
                            + "conflicto de concurrencia. Intenta de nuevo.");
        } finally {
            TenantContext.clear();
        }
    }

    private RestaurantTable newTable(int number) {
        return RestaurantTable.builder()
                .number(number)
                .capacity(4)
                .status(TableStatus.FREE)
                .active(true)
                .build();
    }
}
