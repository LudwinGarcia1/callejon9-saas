package com.callejon9.order;

import com.callejon9.auth.service.AuthService;
import com.callejon9.catalog.service.ProductService;
import com.callejon9.order.service.NewOrderItem;
import com.callejon9.order.service.OrderActor;
import com.callejon9.order.service.OrderService;
import com.callejon9.platform.tenant.service.TenantOnboardingService;
import com.callejon9.sale.domain.PaymentMethod;
import com.callejon9.sale.service.CheckoutService;
import com.callejon9.sale.web.dto.CheckoutRequest;
import com.callejon9.shared.error.BusinessRuleException;
import com.callejon9.table.service.TableService;
import com.callejon9.tenancy.TenantContext;
import com.callejon9.ticket.domain.Ticket;
import com.callejon9.ticket.domain.TicketItemSnapshot;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
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
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.Assertions.fail;

/**
 * El cobro toma una foto de las lineas de la orden y la confirma al final de
 * su transaccion. Si otra operacion modifica la orden en medio, sin esperar
 * su turno, el cambio queda fuera del ticket: un producto entregado y nunca
 * cobrado, o una orden pagada que termina como cancelada o enviada a cocina.
 *
 * <p>Cada prueba fija el orden de la carrera en vez de esperar a que ocurra
 * por casualidad: la primera operacion termina su trabajo pero no confirma,
 * la segunda arranca y la prueba comprueba con {@code pg_blocking_pids} que
 * la transaccion de la primera la bloquea antes de dejarla confirmar. La
 * prueba en paralelo que pide el hallazgo va al final, sin orden fijo.
 *
 * <p>El bloqueo que lo impide es {@code OrderRepository.findForCheckout}
 * ({@code SELECT ... FOR UPDATE}), que comparten el cobro, {@code addItems},
 * {@code cancelOrder}, {@code sendToKitchen} y el avance de cocina.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Cambios a una orden mientras se cobra")
class OrderChangesDuringCheckoutTest {

    private static final String SLUG = "cobro-concurrente";
    private static final long TIMEOUT_SECONDS = 10;
    private static final BigDecimal TACOS_PRICE = new BigDecimal("100.00");
    private static final BigDecimal SODA_PRICE = new BigDecimal("35.00");

    @Autowired private OrderService orderService;
    @Autowired private CheckoutService checkoutService;
    @Autowired private TableService tableService;
    @Autowired private ProductService productService;
    @Autowired private TenantOnboardingService onboardingService;
    @Autowired private AuthService authService;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private TransactionTemplate transactionTemplate;

    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    private UUID tenantId;
    private UUID userId;
    private OrderActor actor;
    private UUID tableId;
    private UUID tacosId;
    private UUID sodaId;

    @BeforeEach
    void seed() {
        tenantId = onboardingService.onboard("Cobro Concurrente", SLUG,
                "admin@cobro-concurrente.com", "Admin", "Secreto123!", "FREE").getId();
        // waiter_id y cashier_id referencian users(id): tiene que ser un usuario real.
        userId = authService.authenticate(SLUG, "admin@cobro-concurrente.com", "Secreto123!")
                .user().getId();
        actor = new OrderActor(userId, true);

        inTenant(() -> {
            tableId = tableService.createTable(1, 4).getId();
            tacosId = productService.createProduct("Tacos al pastor", null, TACOS_PRICE, null).getId();
            sodaId = productService.createProduct("Refresco", null, SODA_PRICE, null).getId();
            return null;
        });
    }

    @AfterEach
    void cleanUp() {
        executor.shutdownNow();
        TenantContext.clear();
        jdbcTemplate.update("DELETE FROM tenants WHERE slug = ?", SLUG);
    }

    @Test
    @DisplayName("P1: un producto agregado mientras se cobra se rechaza y el ticket cuadra con las lineas")
    void itemsAddedDuringCheckoutAreRejected() throws Exception {
        UUID orderId = openOrderWithTacos();

        Race<Ticket> race = secondWaitsForFirst(
                () -> checkout(orderId),
                () -> orderService.addItems(orderId, List.of(new NewOrderItem(sodaId, 1, null)), actor));
        Throwable addItemsError = catchThrowable(() -> race.second().get(TIMEOUT_SECONDS, TimeUnit.SECONDS));

        assertTicketMatchesOrderLines(race.first(), orderId);
        assertRejectedByBusinessRule(addItemsError);
        assertThat(orderStatus(orderId)).isEqualTo("PAID");
    }

    @Test
    @DisplayName("P2: cancelar la orden mientras se cobra se rechaza y la orden queda pagada")
    void cancelDuringCheckoutIsRejected() throws Exception {
        UUID orderId = openOrderWithTacos();

        Race<Ticket> race = secondWaitsForFirst(
                () -> checkout(orderId),
                () -> orderService.cancelOrder(orderId, actor));
        Throwable cancelError = catchThrowable(() -> race.second().get(TIMEOUT_SECONDS, TimeUnit.SECONDS));

        assertThat(orderStatus(orderId))
                .as("una orden cobrada no debe terminar cancelada")
                .isEqualTo("PAID");
        assertRejectedByBusinessRule(cancelError);
        assertTicketMatchesOrderLines(race.first(), orderId);
    }

    @Test
    @DisplayName("P3: un producto agregado justo antes del cobro aparece en el ticket")
    void itemsAddedBeforeCheckoutAreCharged() throws Exception {
        UUID orderId = openOrderWithTacos();

        Race<Object> race = secondWaitsForFirst(
                () -> orderService.addItems(orderId, List.of(new NewOrderItem(sodaId, 1, null)), actor),
                () -> checkout(orderId));
        Ticket ticket = (Ticket) race.second().get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

        assertTicketMatchesOrderLines(ticket, orderId);
        assertThat(ticket.getItemsSnapshot()).extracting(TicketItemSnapshot::productName)
                .containsExactlyInAnyOrder("Tacos al pastor", "Refresco");
        assertThat(ticket.getSubtotal())
                .isEqualByComparingTo(TACOS_PRICE.multiply(BigDecimal.TWO).add(SODA_PRICE));
    }

    @Test
    @DisplayName("enviar a cocina mientras se cobra se rechaza y la orden queda pagada")
    void sendToKitchenDuringCheckoutIsRejected() throws Exception {
        UUID orderId = openOrderWithTacos();

        Race<Ticket> race = secondWaitsForFirst(
                () -> checkout(orderId),
                () -> orderService.sendToKitchen(orderId, actor));
        Throwable sendError = catchThrowable(() -> race.second().get(TIMEOUT_SECONDS, TimeUnit.SECONDS));

        assertThat(orderStatus(orderId))
                .as("una orden cobrada no debe volver a la cocina")
                .isEqualTo("PAID");
        assertRejectedByBusinessRule(sendError);
        assertTicketMatchesOrderLines(race.first(), orderId);
    }

    /**
     * La prueba que pide el hallazgo, sin fijar el orden: ambas operaciones
     * arrancan a la vez. Gane quien gane, el ticket debe cuadrar con las lineas.
     */
    @Test
    @DisplayName("agregar y cobrar en paralelo: en cada ronda el ticket es la suma de las lineas")
    void parallelAddItemsAndCheckoutKeepTheTicketConsistent() throws Exception {
        for (int round = 0; round < 10; round++) {
            UUID orderId = openOrderWithTacos();
            CountDownLatch start = new CountDownLatch(1);

            Future<Ticket> checkout = executor.submit(() -> {
                start.await();
                return inTenant(() -> checkout(orderId));
            });
            Future<?> addItems = executor.submit(() -> {
                start.await();
                return inTenant(() -> orderService.addItems(
                        orderId, List.of(new NewOrderItem(sodaId, 1, null)), actor));
            });
            start.countDown();

            Ticket ticket = checkout.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            Throwable addItemsError = catchThrowable(() -> addItems.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
            if (addItemsError != null) {
                assertRejectedByBusinessRule(addItemsError);
            }
            assertTicketMatchesOrderLines(ticket, orderId);
        }
    }

    private record Race<T>(T first, Future<?> second) {
    }

    /**
     * Ejecuta {@code first} en una transaccion que no confirma hasta que
     * {@code second} queda bloqueada por ella; entonces confirma y devuelve
     * el resultado de la primera junto con la segunda aun en curso.
     */
    private <T> Race<T> secondWaitsForFirst(Supplier<T> first, Supplier<?> second) throws Exception {
        CountDownLatch firstDone = new CountDownLatch(1);
        CountDownLatch commitFirst = new CountDownLatch(1);
        AtomicInteger firstBackendPid = new AtomicInteger();

        Future<T> firstResult = executor.submit(() -> inTenant(() -> transactionTemplate.execute(status -> {
            T result = first.get();
            // La conexion de esta transaccion: la que debe bloquear a la segunda.
            firstBackendPid.set(jdbcTemplate.queryForObject("SELECT pg_backend_pid()", Integer.class));
            firstDone.countDown();
            awaitUninterruptibly(commitFirst);
            return result;
        })));
        assertThat(firstDone.await(TIMEOUT_SECONDS, TimeUnit.SECONDS))
                .as("la primera operacion debe terminar su trabajo antes de confirmar")
                .isTrue();

        Future<?> secondResult = executor.submit(() -> inTenant(second));
        awaitBlockedByOrCompletion(firstBackendPid.get(), secondResult);
        commitFirst.countDown();

        return new Race<>(firstResult.get(TIMEOUT_SECONDS, TimeUnit.SECONDS), secondResult);
    }

    /**
     * La segunda operacion debe quedar bloqueada por la transaccion de la
     * primera (no por cualquier otra sesion de la base) o, si nada la detiene,
     * terminar sin esperar. Lo segundo es justo el defecto que estas pruebas
     * buscan, y lo delatan sus aserciones, no esta espera.
     */
    private void awaitBlockedByOrCompletion(int blockerPid, Future<?> second) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS);
        while (System.nanoTime() < deadline) {
            Integer blocked = jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM pg_stat_activity WHERE ? = ANY(pg_blocking_pids(pid))",
                    Integer.class, blockerPid);
            if ((blocked != null && blocked > 0) || second.isDone()) {
                return;
            }
            Thread.sleep(20);
        }
        fail("la segunda operacion ni quedo bloqueada por la primera ni termino");
    }

    private static void awaitUninterruptibly(CountDownLatch latch) {
        try {
            if (!latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw new IllegalStateException("la prueba no libero la primera transaccion");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(ex);
        }
    }

    private UUID openOrderWithTacos() {
        return inTenant(() -> {
            UUID orderId = orderService.openOrder(tableId, 2, userId).getId();
            orderService.addItems(orderId, List.of(new NewOrderItem(tacosId, 2, null)), actor);
            return orderId;
        });
    }

    private Ticket checkout(UUID orderId) {
        return checkoutService.checkout(
                orderId, new CheckoutRequest(PaymentMethod.CASH, BigDecimal.ZERO, null), userId);
    }

    private void assertTicketMatchesOrderLines(Ticket ticket, UUID orderId) {
        BigDecimal linesTotal = inTenant(() -> transactionTemplate.execute(status -> jdbcTemplate.queryForObject(
                "SELECT coalesce(sum(unit_price * quantity), 0) FROM order_items WHERE order_id = ?",
                BigDecimal.class, orderId)));
        Integer lineCount = inTenant(() -> transactionTemplate.execute(status -> jdbcTemplate.queryForObject(
                "SELECT count(*) FROM order_items WHERE order_id = ?", Integer.class, orderId)));

        assertThat(ticket.getSubtotal())
                .as("el total del ticket debe ser la suma de las lineas de la orden")
                .isEqualByComparingTo(linesTotal);
        assertThat(ticket.getItemsSnapshot())
                .as("cada linea de la orden debe aparecer en el ticket")
                .hasSize(lineCount);
    }

    private static void assertRejectedByBusinessRule(Throwable error) {
        assertThat(error)
                .as("la operacion debe rechazarse porque la orden ya esta pagada")
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(BusinessRuleException.class);
    }

    private String orderStatus(UUID orderId) {
        return inTenant(() -> transactionTemplate.execute(status -> jdbcTemplate.queryForObject(
                "SELECT status FROM orders WHERE id = ?", String.class, orderId)));
    }

    private <T> T inTenant(Supplier<T> action) {
        TenantContext.set(tenantId);
        try {
            return action.get();
        } finally {
            TenantContext.clear();
        }
    }
}
