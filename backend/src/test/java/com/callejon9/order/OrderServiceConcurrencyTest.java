package com.callejon9.order;

import com.callejon9.auth.service.AuthService;
import com.callejon9.order.service.OrderService;
import com.callejon9.platform.tenant.domain.Tenant;
import com.callejon9.platform.tenant.service.TenantOnboardingService;
import com.callejon9.shared.error.BusinessRuleException;
import com.callejon9.table.domain.RestaurantTable;
import com.callejon9.table.service.TableService;
import com.callejon9.tenancy.TenantContext;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Bajo READ COMMITTED, dos peticiones que abren una orden sobre la MISMA mesa
 * al mismo tiempo pueden leer ambas el estado FREE antes de que cualquiera
 * escriba OCCUPIED: no hay error, pero se crean dos ordenes para una sola
 * mesa (doble reservacion silenciosa). Este test usa CountDownLatch para que
 * los dos hilos esten realmente en vuelo a la vez -- llamar al metodo dos
 * veces en secuencia no reproduciria la carrera y pasaria incluso sin el
 * arreglo.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Concurrencia al abrir una orden sobre la misma mesa")
class OrderServiceConcurrencyTest {

    @Autowired private OrderService orderService;
    @Autowired private TableService tableService;
    @Autowired private TenantOnboardingService onboardingService;
    @Autowired private AuthService authService;
    @Autowired private JdbcTemplate jdbcTemplate;

    private UUID tenantId;
    private UUID tableId;
    private UUID waiterId;

    @BeforeEach
    void seed() {
        Tenant tenant = onboardingService.onboard("Concurrencia Test", "concurrencia-test",
                "admin@concurrencia.com", "Admin", "Secreto123!", "FREE");
        tenantId = tenant.getId();
        // waiter_id referencia users(id): tiene que ser un usuario real, no
        // un UUID inventado, o el INSERT de la orden falla por FK antes de
        // siquiera llegar a la carrera que este test quiere reproducir.
        waiterId = authService.authenticate(
                "concurrencia-test", "admin@concurrencia.com", "Secreto123!").user().getId();

        TenantContext.set(tenantId);
        try {
            RestaurantTable table = tableService.createTable(1, 4);
            tableId = table.getId();
        } finally {
            TenantContext.clear();
        }
    }

    @AfterEach
    void cleanUp() {
        TenantContext.clear();
        jdbcTemplate.update("DELETE FROM tenants WHERE slug = 'concurrencia-test'");
    }

    @Test
    @DisplayName("dos meseros abriendo la orden a la vez: exactamente uno gana, "
            + "el otro recibe el conflicto de negocio")
    void exactlyOneThreadOccupiesTheTableWhenOpeningOrdersConcurrently() throws InterruptedException {
        int threadCount = 2;
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);
        AtomicInteger successes = new AtomicInteger();
        List<Throwable> failures = new CopyOnWriteArrayList<>();

        Runnable openOrderTask = () -> {
            TenantContext.set(tenantId);
            try {
                ready.countDown();
                start.await();
                orderService.openOrder(tableId, 2, waiterId);
                successes.incrementAndGet();
            } catch (Throwable ex) {
                failures.add(ex);
            } finally {
                TenantContext.clear();
                done.countDown();
            }
        };

        Thread waiterOne = new Thread(openOrderTask, "waiter-1");
        Thread waiterTwo = new Thread(openOrderTask, "waiter-2");
        waiterOne.start();
        waiterTwo.start();

        // Ambos hilos esperan en la barrera antes de que cualquiera arranque:
        // garantiza que estan realmente en vuelo a la vez, no en secuencia.
        assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();

        assertThat(successes.get())
                .as("exactamente un mesero debe lograr ocupar la mesa")
                .isEqualTo(1);
        assertThat(failures).hasSize(1);
        assertThat(failures.get(0)).isInstanceOf(BusinessRuleException.class);
    }
}
