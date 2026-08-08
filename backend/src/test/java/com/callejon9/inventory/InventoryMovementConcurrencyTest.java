package com.callejon9.inventory;

import com.callejon9.inventory.domain.InventoryItem;
import com.callejon9.inventory.domain.InventoryMovementType;
import com.callejon9.inventory.repository.InventoryItemRepository;
import com.callejon9.inventory.repository.InventoryMovementRepository;
import com.callejon9.inventory.service.InventoryItemService;
import com.callejon9.inventory.service.InventoryMovementService;
import com.callejon9.platform.tenant.domain.Tenant;
import com.callejon9.platform.tenant.service.TenantOnboardingService;
import com.callejon9.tenancy.TenantContext;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
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

/**
 * Bajo READ COMMITTED, dos salidas simultaneas sobre el MISMO insumo pueden
 * leer ambas el mismo stock antes de que cualquiera escriba: el ledger acaba
 * con dos movimientos y la columna stock refleja solo uno. No hay error, no hay
 * excepcion; simplemente el inventario deja de cuadrar.
 *
 * Ese lost update rompe la invariante que sostiene el modulo -- que el ledger
 * reproduzca el stock -- asi que la asercion central no es "el stock vale 10",
 * es "reproducir el ledger con la aritmetica del dominio da el stock guardado".
 *
 * El CountDownLatch es lo que hace valer la prueba: llamar al metodo dos veces
 * en secuencia no reproduce la carrera y pasaria incluso sin el lock.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Concurrencia al mover el mismo insumo")
class InventoryMovementConcurrencyTest {

    @Autowired private InventoryItemService itemService;
    @Autowired private InventoryMovementService movementService;
    @Autowired private InventoryItemRepository itemRepository;
    @Autowired private InventoryMovementRepository movementRepository;
    @Autowired private TenantOnboardingService onboardingService;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private TransactionTemplate transactionTemplate;

    private UUID tenantId;
    private UUID itemId;

    @BeforeEach
    void seed() {
        Tenant tenant = onboardingService.onboard("Inventario Concurrencia", "inv-concurrencia",
                "admin@inv.com", "Admin", "Secreto123!", "FREE");
        tenantId = tenant.getId();

        TenantContext.set(tenantId);
        try {
            // userId nulo a proposito: la columna es nullable y este test no
            // necesita autoria, solo la carrera.
            InventoryItem item = itemService.createItem(
                    "Cebolla", "kg", BigDecimal.ZERO, BigDecimal.ZERO,
                    new BigDecimal("20.000"), null);
            itemId = item.getId();
        } finally {
            TenantContext.clear();
        }
    }

    @AfterEach
    void cleanUp() {
        TenantContext.clear();
        jdbcTemplate.update("DELETE FROM tenants WHERE slug = 'inv-concurrencia'");
    }

    @Test
    @DisplayName("dos salidas a la vez: el stock refleja las dos y cuadra con el ledger")
    void concurrentExitsBothLandAndTheLedgerReconciles() throws InterruptedException {
        int threadCount = 2;
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);
        List<Throwable> failures = new CopyOnWriteArrayList<>();

        Runnable exitTask = () -> {
            TenantContext.set(tenantId);
            try {
                ready.countDown();
                start.await();
                movementService.register(itemId, InventoryMovementType.OUT,
                        new BigDecimal("5.000"), null, null, null);
            } catch (Throwable ex) {
                failures.add(ex);
            } finally {
                TenantContext.clear();
                done.countDown();
            }
        };

        Thread cookOne = new Thread(exitTask, "cook-1");
        Thread cookTwo = new Thread(exitTask, "cook-2");
        cookOne.start();
        cookTwo.start();

        assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();

        assertThat(failures).as("ninguna salida debe fallar: las dos son validas").isEmpty();

        // Las dos lecturas van dentro de una transaccion: el tenant se publica a
        // la sesion de PostgreSQL al abrirla (TenantAwareTransactionManager), y
        // una consulta declarada con @Query no abre transaccion por su cuenta.
        // Fuera de ella, RLS no revela ninguna fila y la suma daria cero.
        TenantContext.set(tenantId);
        try {
            BigDecimal stock = transactionTemplate.execute(status ->
                    itemRepository.findById(itemId).orElseThrow().getStock());
            // Se reconstruye con signedEffect, la misma funcion que usa el
            // servicio: la prueba reproduce el stock, no lo recalcula aparte.
            BigDecimal ledger = transactionTemplate.execute(status ->
                    movementRepository.findByInventoryItemId(itemId).stream()
                            .map(movement -> movement.getMovementType()
                                    .signedEffect(movement.getQuantity()))
                            .reduce(BigDecimal.ZERO, BigDecimal::add));

            assertThat(stock)
                    .as("20 de entrada menos dos salidas de 5 deben dejar 10")
                    .isEqualByComparingTo("10.000");
            assertThat(stock)
                    .as("el ledger reconstruido debe cuadrar con la columna stock")
                    .isEqualByComparingTo(ledger);
        } finally {
            TenantContext.clear();
        }
    }
}
