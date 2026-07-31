package com.callejon9.user;

import com.callejon9.platform.tenant.domain.Tenant;
import com.callejon9.platform.tenant.service.TenantOnboardingService;
import com.callejon9.shared.error.BusinessRuleException;
import com.callejon9.tenancy.TenantContext;
import com.callejon9.user.domain.User;
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
 * Bajo READ COMMITTED, dos peticiones que desactivan a dos administradores
 * DISTINTOS del mismo restaurante, mientras hay exactamente dos activos,
 * pueden leer ambas {@code countByRoleAndActiveTrue == 2} antes de que
 * cualquiera escriba: las dos pasan la validacion "no es el ultimo
 * administrador" y el restaurante se queda sin ningun administrador activo,
 * sin forma de recuperarlo desde la interfaz. Este test usa CountDownLatch
 * para que los dos hilos esten realmente en vuelo a la vez -- llamar al
 * metodo dos veces en secuencia no reproduciria la carrera y pasaria incluso
 * sin el arreglo.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Concurrencia al desactivar administradores del mismo restaurante")
class UserServiceConcurrencyTest {

    @Autowired private UserService userService;
    @Autowired private UserRepository userRepository;
    @Autowired private TenantOnboardingService onboardingService;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private JdbcTemplate jdbcTemplate;

    private UUID adminIdOf(String email) {
        return transactionTemplate.execute(status -> userRepository.findByEmail(email).orElseThrow())
                .getId();
    }

    private UUID tenantId;
    private UUID admin1Id;
    private UUID admin2Id;

    @BeforeEach
    void seed() {
        Tenant tenant = onboardingService.onboard("Concurrencia Usuarios", "concurrencia-usuarios",
                "admin1@concurrencia-usuarios.com", "Admin Uno", "Secreto123!", "FREE");
        tenantId = tenant.getId();

        TenantContext.set(tenantId);
        try {
            admin1Id = adminIdOf("admin1@concurrencia-usuarios.com");
            User admin2 = userService.createUser(
                    "admin2@concurrencia-usuarios.com", "Admin Dos", UserRole.ADMIN, "Secreto123!");
            admin2Id = admin2.getId();
        } finally {
            TenantContext.clear();
        }
    }

    @AfterEach
    void cleanUp() {
        TenantContext.clear();
        jdbcTemplate.update("DELETE FROM tenants WHERE slug = 'concurrencia-usuarios'");
    }

    @Test
    @DisplayName("dos administradores desactivandose entre si a la vez: exactamente uno gana, "
            + "el otro recibe el conflicto de negocio del ultimo administrador")
    void exactlyOneAdminDeactivationSucceedsWhenBothActiveAdminsAreDeactivatedConcurrently()
            throws InterruptedException {
        int threadCount = 2;
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);
        AtomicInteger successes = new AtomicInteger();
        List<Throwable> failures = new CopyOnWriteArrayList<>();

        // Cada hilo desactiva a un administrador distinto, usando al OTRO
        // administrador como quien llama, para no disparar la proteccion de
        // auto-desactivacion (que es una regla distinta a la que este test
        // ejercita).
        Runnable deactivateAdmin1By2 = () -> {
            TenantContext.set(tenantId);
            try {
                ready.countDown();
                start.await();
                userService.setActive(admin1Id, false, admin2Id);
                successes.incrementAndGet();
            } catch (Throwable ex) {
                failures.add(ex);
            } finally {
                TenantContext.clear();
                done.countDown();
            }
        };
        Runnable deactivateAdmin2By1 = () -> {
            TenantContext.set(tenantId);
            try {
                ready.countDown();
                start.await();
                userService.setActive(admin2Id, false, admin1Id);
                successes.incrementAndGet();
            } catch (Throwable ex) {
                failures.add(ex);
            } finally {
                TenantContext.clear();
                done.countDown();
            }
        };

        Thread t1 = new Thread(deactivateAdmin1By2, "deactivate-admin-1");
        Thread t2 = new Thread(deactivateAdmin2By1, "deactivate-admin-2");
        t1.start();
        t2.start();

        // Ambos hilos esperan en la barrera antes de que cualquiera arranque:
        // garantiza que estan realmente en vuelo a la vez, no en secuencia.
        assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();

        assertThat(successes.get())
                .as("exactamente una desactivacion debe tener exito")
                .isEqualTo(1);
        assertThat(failures).hasSize(1);
        assertThat(failures.get(0)).isInstanceOf(BusinessRuleException.class);

        TenantContext.set(tenantId);
        try {
            long activeAdmins = transactionTemplate.execute(
                    status -> userRepository.countByRoleAndActiveTrue(UserRole.ADMIN));
            assertThat(activeAdmins)
                    .as("el restaurante nunca debe quedar sin administradores activos")
                    .isEqualTo(1);
        } finally {
            TenantContext.clear();
        }
    }
}
