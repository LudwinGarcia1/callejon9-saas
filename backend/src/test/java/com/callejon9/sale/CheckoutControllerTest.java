package com.callejon9.sale;

import com.callejon9.auth.service.JwtService;
import com.callejon9.catalog.domain.Product;
import com.callejon9.catalog.repository.ProductRepository;
import com.callejon9.platform.tenant.domain.Tenant;
import com.callejon9.platform.tenant.service.TenantOnboardingService;
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

    @Autowired private MockMvc mockMvc;
    @Autowired private TenantOnboardingService onboardingService;
    @Autowired private JwtService jwtService;
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
        jdbcTemplate.update("DELETE FROM tenants WHERE slug = 'checkout-test'");
    }

    private Cookie cookieFor(User user) {
        return new Cookie("access_token", jwtService.generateAccessToken(user));
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
