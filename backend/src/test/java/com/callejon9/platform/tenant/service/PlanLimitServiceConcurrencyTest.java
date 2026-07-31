package com.callejon9.platform.tenant.service;

import com.callejon9.platform.tenant.domain.Tenant;
import com.callejon9.shared.error.BusinessRuleException;
import com.callejon9.tenancy.TenantContext;
import com.callejon9.user.domain.UserRole;
import com.callejon9.user.repository.UserRepository;
import com.callejon9.user.service.UserService;
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
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Bajo READ COMMITTED, dos altas de usuario concurrentes en un tenant FREE
 * (limite de 3 usuarios) que ya tiene 2 pueden leer ambas
 * {@code currentUsers == 2 < 3} antes de que cualquiera inserte: las dos
 * pasan {@link PlanLimitService#assertCanAddUser} y el tenant termina con 4
 * usuarios, uno por encima del tope que este bloqueo existe para hacer
 * cumplir. Este test usa CountDownLatch para que los dos hilos esten
 * realmente en vuelo a la vez -- llamar al metodo dos veces en secuencia no
 * reproduciria la carrera y pasaria incluso sin el arreglo.
 *
 * <p>Se ejercita a traves de {@link UserService#createUser}, la ruta real por
 * la que {@code PlanLimitService.assertCanAddUser} se invoca (POST
 * /api/v1/users): el "leer conteo, decidir" vive en el servicio de planes,
 * pero el "escribir" (el INSERT del usuario) vive en {@code UserService}, y
 * la carrera solo se reproduce cubriendo las dos mitades juntas.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Concurrencia al topar el limite de usuarios del plan")
class PlanLimitServiceConcurrencyTest {

    @Autowired private UserService userService;
    @Autowired private UserRepository userRepository;
    @Autowired private TenantOnboardingService onboardingService;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private JdbcTemplate jdbcTemplate;

    private UUID tenantId;

    @BeforeEach
    void seed() {
        Tenant tenant = onboardingService.onboard("Concurrencia Plan", "concurrencia-plan",
                "admin@concurrencia-plan.com", "Admin", "Secreto123!", "FREE");
        tenantId = tenant.getId();

        // El onboarding ya creo 1 administrador; el plan FREE permite 3. Se
        // agrega un segundo usuario SECUENCIALMENTE para llegar a 2, el punto
        // de partida exacto que el escenario de la carrera describe.
        TenantContext.set(tenantId);
        try {
            userService.createUser("mesero1@concurrencia-plan.com", "Mesero Uno",
                    UserRole.WAITER, "Secreto123!");
        } finally {
            TenantContext.clear();
        }
    }

    @AfterEach
    void cleanUp() {
        TenantContext.clear();
        jdbcTemplate.update("DELETE FROM tenants WHERE slug = 'concurrencia-plan'");
    }

    @Test
    @DisplayName("dos altas de usuario a la vez sobre el tercer cupo: exactamente una gana, "
            + "la otra recibe el conflicto de negocio del limite del plan")
    void exactlyOneUserCreationSucceedsWhenBothRaceForTheLastPlanSlot() throws InterruptedException {
        int threadCount = 2;
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);
        AtomicInteger successes = new AtomicInteger();
        List<Throwable> failures = new CopyOnWriteArrayList<>();

        Runnable createSecondWaiter = () -> {
            TenantContext.set(tenantId);
            try {
                ready.countDown();
                start.await();
                userService.createUser("mesero2@concurrencia-plan.com", "Mesero Dos",
                        UserRole.WAITER, "Secreto123!");
                successes.incrementAndGet();
            } catch (Throwable ex) {
                failures.add(ex);
            } finally {
                TenantContext.clear();
                done.countDown();
            }
        };
        Runnable createThirdWaiter = () -> {
            TenantContext.set(tenantId);
            try {
                ready.countDown();
                start.await();
                userService.createUser("mesero3@concurrencia-plan.com", "Mesero Tres",
                        UserRole.WAITER, "Secreto123!");
                successes.incrementAndGet();
            } catch (Throwable ex) {
                failures.add(ex);
            } finally {
                TenantContext.clear();
                done.countDown();
            }
        };

        Thread t1 = new Thread(createSecondWaiter, "create-user-1");
        Thread t2 = new Thread(createThirdWaiter, "create-user-2");
        t1.start();
        t2.start();

        // Ambos hilos esperan en la barrera antes de que cualquiera arranque:
        // garantiza que estan realmente en vuelo a la vez, no en secuencia.
        assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();

        assertThat(successes.get())
                .as("exactamente una alta de usuario debe tener exito")
                .isEqualTo(1);
        assertThat(failures).hasSize(1);
        assertThat(failures.get(0)).isInstanceOf(BusinessRuleException.class);

        TenantContext.set(tenantId);
        try {
            long totalUsers = transactionTemplate.execute(
                    status -> userRepository.countByTenantId(tenantId));
            assertThat(totalUsers)
                    .as("el tenant FREE nunca debe superar su tope de 3 usuarios")
                    .isEqualTo(3);
        } finally {
            TenantContext.clear();
        }
    }
}
