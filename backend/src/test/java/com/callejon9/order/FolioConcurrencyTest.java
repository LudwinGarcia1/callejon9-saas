package com.callejon9.order;

import com.callejon9.auth.service.AuthService;
import com.callejon9.order.domain.Order;
import com.callejon9.order.domain.OrderStatus;
import com.callejon9.order.repository.OrderRepository;
import com.callejon9.order.service.FolioGenerator;
import com.callejon9.platform.tenant.domain.Tenant;
import com.callejon9.platform.tenant.service.TenantOnboardingService;
import com.callejon9.tenancy.TenantContext;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("CAL-13: Concurrencia de folios unicos entre instancias en PostgreSQL")
class FolioConcurrencyTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private TenantOnboardingService onboardingService;

    @Autowired
    private AuthService authService;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private UUID tenantId;
    private UUID waiterId;

    @BeforeEach
    void seed() {
        Tenant tenant = onboardingService.onboard(
                "Folio Concurrency Test", "folio-concurrency",
                "admin@folioconcurrency.com", "Admin", "Secreto123!", "FREE");
        tenantId = tenant.getId();
        waiterId = authService.authenticate(
                "folio-concurrency", "admin@folioconcurrency.com", "Secreto123!").user().getId();
    }

    @AfterEach
    void cleanUp() {
        TenantContext.clear();
        jdbcTemplate.update("DELETE FROM tenants WHERE slug = 'folio-concurrency'");
    }

    @Test
    @DisplayName("multiples instancias generan folios estrictamente unicos bajo trafico concurrente")
    void multipleInstancesGenerateUniqueFoliosConcurrently() throws InterruptedException {
        // Simulamos 5 instancias independientes de FolioGenerator (como tareas ECS distintas)
        List<FolioGenerator> instances = List.of(
                new FolioGenerator(jdbcTemplate),
                new FolioGenerator(jdbcTemplate),
                new FolioGenerator(jdbcTemplate),
                new FolioGenerator(jdbcTemplate),
                new FolioGenerator(jdbcTemplate)
        );

        int totalThreads = 30;
        ExecutorService executor = Executors.newFixedThreadPool(totalThreads);
        CountDownLatch readyLatch = new CountDownLatch(totalThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(totalThreads);

        List<String> generatedFolios = new CopyOnWriteArrayList<>();
        List<Throwable> errors = new CopyOnWriteArrayList<>();

        for (int i = 0; i < totalThreads; i++) {
            final FolioGenerator generator = instances.get(i % instances.size());
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    TenantContext.set(tenantId);
                    try {
                        transactionTemplate.executeWithoutResult(status -> {
                            String folio = generator.next();
                            generatedFolios.add(folio);

                            // Guardamos la orden en PostgreSQL para demostrar que no colisiona
                            // con el indice UNIQUE (tenant_id, folio)
                            orderRepository.save(Order.builder()
                                    .folio(folio)
                                    .waiterId(waiterId)
                                    .guestCount(2)
                                    .status(OrderStatus.NEW)
                                    .total(BigDecimal.ZERO)
                                    .openedAt(Instant.now())
                                    .build());
                        });
                    } finally {
                        TenantContext.clear();
                    }
                } catch (Throwable t) {
                    errors.add(t);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown(); // Disparamos todos los hilos simultaneamente
        boolean completed = doneLatch.await(15, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completed).isTrue();
        assertThat(errors).isEmpty();
        assertThat(generatedFolios).hasSize(totalThreads);

        // Verificamos que todos los folios sigan el formato esperado
        for (String folio : generatedFolios) {
            assertThat(folio).matches("ORD-\\d{12}");
        }

        // Verificamos unicidad absoluta: ningún folio se repite
        Set<String> uniqueFolios = new HashSet<>(generatedFolios);
        assertThat(uniqueFolios).hasSize(totalThreads);

        // Verificamos que en la base de datos se guardaron efectivamente todas las ordenes
        TenantContext.set(tenantId);
        try {
            transactionTemplate.executeWithoutResult(status -> {
                long orderCount = orderRepository.count();
                assertThat(orderCount).isEqualTo(totalThreads);
            });
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    @DisplayName("los prefijos ORD- y TCK- avanzan de forma independiente para el mismo inquilino")
    void differentPrefixesAdvanceIndependently() {
        FolioGenerator generator = new FolioGenerator(jdbcTemplate);
        TenantContext.set(tenantId);
        try {
            transactionTemplate.executeWithoutResult(status -> {
                String orderFolio = generator.next("ORD-");
                String ticketFolio = generator.next("TCK-");

                assertThat(orderFolio).matches("ORD-\\d{12}");
                assertThat(ticketFolio).matches("TCK-\\d{12}");

                // Ambos registros se crean en tenant_folio_sequences con prefijos distintos
                Integer sequenceRows = jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM tenant_folio_sequences WHERE tenant_id = ?",
                        Integer.class, tenantId);
                assertThat(sequenceRows).isEqualTo(2);
            });
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    @DisplayName("el aislamiento por RLS impide que un inquilino vea o altere secuencias de otro")
    void tenantIsolationAppliesToFolioSequences() {
        // Creamos un segundo tenant
        Tenant tenant2 = onboardingService.onboard(
                "Tenant Secundario", "tenant-secundario",
                "admin@secundario.com", "Admin", "Secreto123!", "FREE");
        UUID tenant2Id = tenant2.getId();

        FolioGenerator generator = new FolioGenerator(jdbcTemplate);

        try {
            // Tenant 1 genera folio
            TenantContext.set(tenantId);
            try {
                transactionTemplate.executeWithoutResult(status -> generator.next("ORD-"));
            } finally {
                TenantContext.clear();
            }

            // Tenant 2 genera folio
            TenantContext.set(tenant2Id);
            try {
                transactionTemplate.executeWithoutResult(status -> {
                    generator.next("ORD-");

                    // Tenant 2 solo ve 1 fila en tenant_folio_sequences (la suya propia)
                    Integer visibleRows = jdbcTemplate.queryForObject(
                            "SELECT count(*) FROM tenant_folio_sequences",
                            Integer.class);
                    assertThat(visibleRows).isEqualTo(1);
                });
            } finally {
                TenantContext.clear();
            }

            // Sin tenant no se ve ninguna fila (fail-closed de RLS)
            TenantContext.clear();
            transactionTemplate.executeWithoutResult(status -> {
                Integer visibleRows = jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM tenant_folio_sequences",
                        Integer.class);
                assertThat(visibleRows).isEqualTo(0);
            });
        } finally {
            jdbcTemplate.update("DELETE FROM tenants WHERE slug = 'tenant-secundario'");
        }
    }
}
