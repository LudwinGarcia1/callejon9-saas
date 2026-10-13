package com.callejon9.sale;

import com.callejon9.catalog.domain.Product;
import com.callejon9.catalog.repository.ProductRepository;
import com.callejon9.platform.tenant.domain.Tenant;
import com.callejon9.platform.tenant.service.TenantOnboardingService;
import com.callejon9.support.TestSessions;
import com.callejon9.table.domain.RestaurantTable;
import com.callejon9.table.domain.TableStatus;
import com.callejon9.table.repository.RestaurantTableRepository;
import com.callejon9.tenancy.TenantContext;
import com.callejon9.user.domain.User;
import com.callejon9.user.domain.UserRole;
import com.callejon9.user.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * El checkout: cierra la cuenta de una orden en una sola transaccion (venta +
 * ticket inmutable + orden PAID + mesa libre). Sigue el mismo patron de
 * fixtures que OrderControllerTest: waiter_id y cashier_id tienen FK a
 * users, asi que el usuario autenticado debe existir realmente.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Checkout")
class CheckoutControllerTest {
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean
    private com.callejon9.ticket.repository.TicketRepository ticketRepository;

    @Autowired private MockMvc mockMvc;
    @Autowired private TenantOnboardingService onboardingService;
    @Autowired private TestSessions testSessions;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private UserRepository userRepository;
    @Autowired private RestaurantTableRepository tableRepository;
    @Autowired private ProductRepository productRepository;

    private Tenant tenant;
    private User waiter;
    private User cashier;
    private RestaurantTable table;

    @BeforeEach
    void seed() {
        tenant = onboardingService.onboard("Checkout Test", "checkout-test",
                "admin@checkout.com", "Admin", "Secreto123!", "FREE");

        TenantContext.set(tenant.getId());
        try {
            waiter = transactionTemplate.execute(status -> userRepository.save(User.builder()
                    .email("mesero@checkout.com").passwordHash("x").fullName("Mesero")
                    .role(UserRole.WAITER).active(true).build()));
            cashier = transactionTemplate.execute(status -> userRepository.save(User.builder()
                    .email("cajero@checkout.com").passwordHash("x").fullName("Cajero")
                    .role(UserRole.CASHIER).active(true).build()));
            table = transactionTemplate.execute(status -> tableRepository.save(RestaurantTable.builder()
                    .number(1).capacity(4).status(TableStatus.FREE).active(true).build()));
        } finally {
            TenantContext.clear();
        }
    }

    @AfterEach
    void cleanUp() {
        TenantContext.clear();
        jdbcTemplate.update("DELETE FROM tenants WHERE slug IN ('checkout-test', 'checkout-other')");
    }

    private Cookie cookieFor(User user) {
        return new Cookie("access_token", testSessions.accessTokenFor(user));
    }

    private UUID orderFor500() throws Exception {
        var id = openOrder();
        addItem(id, createProduct("Cuenta", "500.00"), 1);
        return id;
    }

    @Test
    void failureAfterPaymentsAreFlushedRollsBackEntireCheckout() throws Exception {
        var id = orderFor500();
        org.mockito.Mockito.doThrow(new IllegalStateException("forced ticket failure"))
                .when(ticketRepository).save(org.mockito.ArgumentMatchers.any(com.callejon9.ticket.domain.Ticket.class));
        try {
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> mockMvc.perform(
                    post("/api/v1/orders/" + id + "/checkout").cookie(cookieFor(cashier))
                    .contentType(MediaType.APPLICATION_JSON).content("{\"tipPercent\":0,\"payments\":[{\"method\":\"CASH\",\"amount\":500}]}")))
                    .hasRootCauseInstanceOf(IllegalStateException.class);
        } finally { org.mockito.Mockito.reset(ticketRepository); }
        TenantContext.set(tenant.getId());
        try {
            transactionTemplate.executeWithoutResult(s -> {
                for (var name : java.util.List.of("sales", "tickets", "payments"))
                    assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM " + name, Integer.class)).isZero();
                assertThat(jdbcTemplate.queryForObject("SELECT status FROM orders WHERE id=?", String.class, id)).isEqualTo("NEW");
                assertThat(jdbcTemplate.queryForObject("SELECT status FROM restaurant_tables WHERE id=?", String.class, table.getId())).isEqualTo("OCCUPIED");
            });
        } finally { TenantContext.clear(); }
    }

    @Test
    void concurrentCheckoutCreatesOnlyOneSaleAndPayment() throws Exception {
        var id = orderFor500();
        var start = new java.util.concurrent.CountDownLatch(1);
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<Integer> checkout = () -> {
                start.await();
                return mockMvc.perform(post("/api/v1/orders/" + id + "/checkout").cookie(cookieFor(cashier))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"tipPercent\":0,\"payments\":[{\"method\":\"CASH\",\"amount\":500}]}"))
                        .andReturn().getResponse().getStatus();
            };
            var first = executor.submit(checkout); var second = executor.submit(checkout); start.countDown();
            assertThat(java.util.List.of(first.get(30, java.util.concurrent.TimeUnit.SECONDS),
                    second.get(30, java.util.concurrent.TimeUnit.SECONDS))).containsExactlyInAnyOrder(201, 409);
        }
        TenantContext.set(tenant.getId());
        try { transactionTemplate.executeWithoutResult(s -> {
            assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM sales", Integer.class)).isEqualTo(1);
            assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM payments", Integer.class)).isEqualTo(1);
        }); } finally { TenantContext.clear(); }
    }

    @Test
    void paymentsAreInvisibleAndRejectForeignTenantInsert() throws Exception {
        var id = orderFor500();
        var response = mockMvc.perform(post("/api/v1/orders/" + id + "/checkout").cookie(cookieFor(cashier))
                .contentType(MediaType.APPLICATION_JSON).content("{\"tipPercent\":0,\"payments\":[{\"method\":\"CASH\",\"amount\":500}]}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        var saleId = UUID.fromString(new com.fasterxml.jackson.databind.ObjectMapper().readTree(response).get("saleId").asText());
        var otherTenant = onboardingService.onboard("Other Restaurant", "checkout-other",
                "admin@other.example", "Admin", "Secreto123!", "FREE");
        TenantContext.set(otherTenant.getId());
        try {
            assertThat(transactionTemplate.<Integer>execute(s -> jdbcTemplate.queryForObject("SELECT count(*) FROM payments", Integer.class))).isZero();
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(s ->
                    jdbcTemplate.update("INSERT INTO payments(tenant_id,sale_id,provider,method,amount,received_amount,status) VALUES(?,?,'MANUAL','CASH',1,1,'COMPLETED')",
                            tenant.getId(), saleId)))
                    .rootCause().isInstanceOfSatisfying(java.sql.SQLException.class,
                            e -> assertThat(e.getSQLState()).isEqualTo("42501"));
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(s ->
                    jdbcTemplate.update("INSERT INTO payments(tenant_id,sale_id,provider,method,amount,received_amount,status) VALUES(?,?,'MANUAL','CASH',1,1,'COMPLETED')",
                            otherTenant.getId(), saleId)))
                    .rootCause().isInstanceOfSatisfying(java.sql.SQLException.class,
                            e -> assertThat(e.getSQLState()).isEqualTo("23503"));
        } finally { TenantContext.clear(); }
    }

    @Test
    void mixedPaymentsAreStoredAndAppearInTicketHistoryAndAnalytics() throws Exception {
        var id = orderFor500();
        var body = mockMvc.perform(post("/api/v1/orders/" + id + "/checkout")
                .cookie(cookieFor(cashier)).contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"tipPercent":0,"payments":[{"method":"CASH","amount":300},{"method":"CARD","amount":200}]}
                    """))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.paymentMethod").value("MIXED"))
                .andExpect(jsonPath("$.payments.length()").value(2))
                .andExpect(jsonPath("$.payments[0].amount").value(300))
                .andExpect(jsonPath("$.change").value(0)).andReturn().getResponse().getContentAsString();
        var json = new com.fasterxml.jackson.databind.ObjectMapper().readTree(body);
        mockMvc.perform(get("/api/v1/tickets/" + json.get("id").asText()).cookie(cookieFor(cashier)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.payments[1].amount").value(200));
        TenantContext.set(tenant.getId());
        try {
            assertThat(transactionTemplate.<Integer>execute(s -> jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM payments WHERE sale_id = ?", Integer.class,
                    UUID.fromString(json.get("saleId").asText())))).isEqualTo(2);
        } finally { TenantContext.clear(); }
        mockMvc.perform(get("/api/v1/sales").cookie(cookieFor(cashier)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.paymentMix[0].method").value("CASH"))
                .andExpect(jsonPath("$.paymentMix[0].total").value(300))
                .andExpect(jsonPath("$.paymentMix[1].total").value(200));
        TenantContext.set(tenant.getId());
        UUID adminId;
        try { adminId = transactionTemplate.execute(s -> jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE role = 'ADMIN'", UUID.class)); }
        finally { TenantContext.clear(); }
        var admin = User.builder().role(UserRole.ADMIN).build();
        admin.setId(adminId); admin.setTenantId(tenant.getId());
        mockMvc.perform(get("/api/v1/analytics").cookie(cookieFor(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.paymentMix[0].total").value(300))
                .andExpect(jsonPath("$.paymentMix[1].total").value(200));
    }

    @Test
    void cashOverpaymentProducesChangeAndAppliedPaymentOnly() throws Exception {
        var id = orderFor500();
        mockMvc.perform(post("/api/v1/orders/" + id + "/checkout").cookie(cookieFor(cashier))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tipPercent\":0,\"payments\":[{\"method\":\"CASH\",\"amount\":600}]}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.payments[0].amount").value(500))
                .andExpect(jsonPath("$.payments[0].receivedAmount").value(600))
                .andExpect(jsonPath("$.change").value(100));
    }

    @Test
    void invalidPaymentTotalsLeaveNoSaleTicketOrPayments() throws Exception {
        var id = orderFor500();
        for (var payment : java.util.List.of("{\"method\":\"CASH\",\"amount\":400}",
                "{\"method\":\"CARD\",\"amount\":600}", "{\"method\":\"MIXED\",\"amount\":500}")) {
            mockMvc.perform(post("/api/v1/orders/" + id + "/checkout").cookie(cookieFor(cashier))
                    .contentType(MediaType.APPLICATION_JSON).content("{\"tipPercent\":0,\"payments\":[" + payment + "]}"))
                    .andExpect(status().isUnprocessableEntity());
        }
        TenantContext.set(tenant.getId());
        try {
            transactionTemplate.executeWithoutResult(s -> {
                for (var name : java.util.List.of("sales", "tickets", "payments"))
                    assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM " + name, Integer.class)).isZero();
            });
        } finally { TenantContext.clear(); }
    }

    private Product createProduct(String name, String price) {
        TenantContext.set(tenant.getId());
        try {
            return transactionTemplate.execute(status -> productRepository.save(Product.builder()
                    .name(name).description(name).price(new BigDecimal(price)).active(true).build()));
        } finally {
            TenantContext.clear();
        }
    }

    private UUID openOrder() throws Exception {
        String body = mockMvc.perform(post("/api/v1/orders")
                        .cookie(cookieFor(waiter))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tableId\":\"" + table.getId() + "\",\"guestCount\":2}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(body.replaceAll(".*\"id\":\"([0-9a-fA-F-]+)\".*", "$1"));
    }

    private void addItem(UUID orderId, Product product, int quantity) throws Exception {
        mockMvc.perform(post("/api/v1/orders/" + orderId + "/items")
                        .cookie(cookieFor(waiter))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"productId\":\"" + product.getId()
                                + "\",\"quantity\":" + quantity + "}]}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("cobra la cuenta: calcula subtotal, propina (redondeo HALF_UP) y total")
    void checkoutComputesSubtotalTipAndTotalWithRounding() throws Exception {
        // 19.99 * 15% = 2.9985 -> redondea a 3.00 (no divide de forma exacta).
        Product product = createProduct("Taco", "19.99");
        UUID orderId = openOrder();
        addItem(orderId, product, 1);

        mockMvc.perform(post("/api/v1/orders/" + orderId + "/checkout")
                        .cookie(cookieFor(cashier))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentMethod\":\"CASH\",\"tipPercent\":15}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.subtotal").value(19.99))
                .andExpect(jsonPath("$.tip").value(3.00))
                .andExpect(jsonPath("$.total").value(22.99))
                .andExpect(jsonPath("$.paymentMethod").value("CASH"))
                .andExpect(jsonPath("$.folio").value(org.hamcrest.Matchers.matchesPattern("TCK-\\d{12}")));
    }

    @Test
    @DisplayName("el items_snapshot del ticket no cambia si el precio del producto cambia despues")
    void ticketSnapshotIsImmutableAfterCheckout() throws Exception {
        Product product = createProduct("Taco", "25.00");
        UUID orderId = openOrder();
        addItem(orderId, product, 2);

        String ticketBody = mockMvc.perform(post("/api/v1/orders/" + orderId + "/checkout")
                        .cookie(cookieFor(cashier))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentMethod\":\"CARD\",\"tipPercent\":10}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.items[0].unitPrice").value(25.00))
                .andReturn().getResponse().getContentAsString();
        UUID ticketId = UUID.fromString(ticketBody.replaceAll(".*\"id\":\"([0-9a-fA-F-]+)\".*", "$1"));

        TenantContext.set(tenant.getId());
        try {
            transactionTemplate.executeWithoutResult(status -> {
                Product reloaded = productRepository.findById(product.getId()).orElseThrow();
                reloaded.setPrice(new BigDecimal("99.00"));
                productRepository.save(reloaded);
            });
        } finally {
            TenantContext.clear();
        }

        mockMvc.perform(get("/api/v1/tickets/" + ticketId).cookie(cookieFor(cashier)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].unitPrice").value(25.00))
                .andExpect(jsonPath("$.items[0].productName").value("Taco"))
                .andExpect(jsonPath("$.subtotal").value(50.00))
                .andExpect(jsonPath("$.total").value(55.00));
    }

    @Test
    @DisplayName("cobrar una orden ya PAID da 409")
    void doubleCheckoutIsRejected() throws Exception {
        Product product = createProduct("Taco", "25.00");
        UUID orderId = openOrder();
        addItem(orderId, product, 1);

        mockMvc.perform(post("/api/v1/orders/" + orderId + "/checkout")
                        .cookie(cookieFor(cashier))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentMethod\":\"CASH\",\"tipPercent\":0}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/orders/" + orderId + "/checkout")
                        .cookie(cookieFor(cashier))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentMethod\":\"CASH\",\"tipPercent\":0}"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("al cobrar, la mesa vuelve a quedar FREE")
    void tableReturnsToFreeAfterCheckout() throws Exception {
        Product product = createProduct("Taco", "25.00");
        UUID orderId = openOrder();
        addItem(orderId, product, 1);

        mockMvc.perform(get("/api/v1/tables").cookie(cookieFor(cashier)))
                .andExpect(jsonPath("$[0].status").value("OCCUPIED"));

        mockMvc.perform(post("/api/v1/orders/" + orderId + "/checkout")
                        .cookie(cookieFor(cashier))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentMethod\":\"CASH\",\"tipPercent\":0}"))
                .andExpect(status().isCreated());

        TenantContext.set(tenant.getId());
        try {
            RestaurantTable reloaded = transactionTemplate.execute(
                    status -> tableRepository.findById(table.getId()).orElseThrow());
            assertThat(reloaded.getStatus()).isEqualTo(TableStatus.FREE);
            assertThat(reloaded.getWaiterId()).isNull();
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    @DisplayName("caja lee la orden que va a cobrar, con sus renglones")
    void cashierReadsTheOrderBeforeCheckout() throws Exception {
        Product product = createProduct("Taco", "25.00");
        UUID orderId = openOrder();
        addItem(orderId, product, 2);

        mockMvc.perform(get("/api/v1/orders/" + orderId).cookie(cookieFor(cashier)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(orderId.toString()))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.total").value(50.00));

        mockMvc.perform(get("/api/v1/orders").cookie(cookieFor(cashier)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(orderId.toString()));
    }

    @Test
    @DisplayName("WAITER no puede cobrar la cuenta")
    void waiterCannotCheckout() throws Exception {
        Product product = createProduct("Taco", "25.00");
        UUID orderId = openOrder();
        addItem(orderId, product, 1);

        mockMvc.perform(post("/api/v1/orders/" + orderId + "/checkout")
                        .cookie(cookieFor(waiter))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentMethod\":\"CASH\",\"tipPercent\":0}"))
                .andExpect(status().isForbidden());
    }
}
