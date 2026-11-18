package com.callejon9.order;

import com.callejon9.catalog.domain.Product;
import com.callejon9.catalog.repository.ProductRepository;
import com.callejon9.order.domain.Order;
import com.callejon9.order.domain.OrderStatus;
import com.callejon9.order.repository.OrderItemRepository;
import com.callejon9.order.repository.OrderRepository;
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
import org.junit.jupiter.api.Nested;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Deficits de seguridad del modulo de mesero y su correccion. Cada grupo
 * documenta un deficit: antes de la correccion estas peticiones respondian
 * 200/201 y modificaban datos; ahora se rechazan sin efectos.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Seguridad del modulo de mesero")
class WaiterOrderSecurityTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private TenantOnboardingService onboardingService;
    @Autowired private TestSessions testSessions;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private UserRepository userRepository;
    @Autowired private RestaurantTableRepository tableRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private OrderItemRepository orderItemRepository;

    private Tenant tenant;
    private User admin;
    private User waiterA;
    private User waiterB;
    private RestaurantTable table;
    private Product product;

    @BeforeEach
    void seed() {
        tenant = onboardingService.onboard("Mesero Seguridad", "mesero-seguridad",
                "admin@meseroseg.com", "Admin", "Secreto123!", "FREE");

        TenantContext.set(tenant.getId());
        try {
            admin = transactionTemplate.execute(
                    status -> userRepository.findByEmail("admin@meseroseg.com").orElseThrow());
            waiterA = transactionTemplate.execute(status -> userRepository.save(User.builder()
                    .email("mesero.a@meseroseg.com").passwordHash("x").fullName("Mesero A")
                    .role(UserRole.WAITER).active(true).build()));
            waiterB = transactionTemplate.execute(status -> userRepository.save(User.builder()
                    .email("mesero.b@meseroseg.com").passwordHash("x").fullName("Mesero B")
                    .role(UserRole.WAITER).active(true).build()));
            table = transactionTemplate.execute(status -> tableRepository.save(RestaurantTable.builder()
                    .number(1).capacity(4).status(TableStatus.FREE).active(true).build()));
            product = transactionTemplate.execute(status -> productRepository.save(Product.builder()
                    .name("Taco").description("Taco al pastor")
                    .price(new BigDecimal("25.00")).active(true).build()));
        } finally {
            TenantContext.clear();
        }
    }

    @AfterEach
    void cleanUp() {
        TenantContext.clear();
        jdbcTemplate.update("DELETE FROM tenants WHERE slug = 'mesero-seguridad'");
    }

    private Cookie cookieFor(User user) {
        return new Cookie("access_token", testSessions.accessTokenFor(user));
    }

    private UUID openOrderAs(User user) throws Exception {
        String body = mockMvc.perform(post("/api/v1/orders")
                        .cookie(cookieFor(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tableId\":\"" + table.getId() + "\",\"guestCount\":2}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(body.replaceAll(".*\"id\":\"([0-9a-fA-F-]+)\".*", "$1"));
    }

    private String itemBody(int quantity) {
        return "{\"items\":[{\"productId\":\"" + product.getId() + "\",\"quantity\":" + quantity + "}]}";
    }

    private void addItemAs(User user, UUID orderId) throws Exception {
        mockMvc.perform(post("/api/v1/orders/" + orderId + "/items")
                        .cookie(cookieFor(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(itemBody(1)))
                .andExpect(status().isOk());
    }

    private Order reload(UUID orderId) {
        TenantContext.set(tenant.getId());
        try {
            return transactionTemplate.execute(status -> orderRepository.findById(orderId).orElseThrow());
        } finally {
            TenantContext.clear();
        }
    }

    private int itemCount(UUID orderId) {
        TenantContext.set(tenant.getId());
        try {
            return transactionTemplate.execute(
                    status -> orderItemRepository.findByOrderIdOrderByOrderedAt(orderId).size());
        } finally {
            TenantContext.clear();
        }
    }

    @Nested
    @DisplayName("Deficit 1: un mesero opera sobre ordenes de otro mesero (BOLA/IDOR)")
    class OrderOwnership {

        @Test
        @DisplayName("el mesero B no puede agregar productos a la orden del mesero A")
        void anotherWaiterCannotAddItems() throws Exception {
            UUID orderId = openOrderAs(waiterA);

            mockMvc.perform(post("/api/v1/orders/" + orderId + "/items")
                            .cookie(cookieFor(waiterB))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(itemBody(3)))
                    .andExpect(status().isForbidden());

            assertThat(itemCount(orderId)).isZero();
            assertThat(reload(orderId).getTotal()).isEqualByComparingTo("0");
        }

        @Test
        @DisplayName("el mesero B no puede enviar a cocina ni cancelar la orden del mesero A")
        void anotherWaiterCannotSendOrCancel() throws Exception {
            UUID orderId = openOrderAs(waiterA);
            addItemAs(waiterA, orderId);

            mockMvc.perform(post("/api/v1/orders/" + orderId + "/send-to-kitchen")
                            .cookie(cookieFor(waiterB)))
                    .andExpect(status().isForbidden());
            mockMvc.perform(post("/api/v1/orders/" + orderId + "/cancel")
                            .cookie(cookieFor(waiterB)))
                    .andExpect(status().isForbidden());

            assertThat(reload(orderId).getStatus()).isEqualTo(OrderStatus.NEW);
        }

        @Test
        @DisplayName("CAL-151: el mesero B no puede quitar lineas de la orden del mesero A")
        void anotherWaiterCannotRemoveItems() throws Exception {
            UUID orderId = openOrderAs(waiterA);
            addItemAs(waiterA, orderId);
            UUID itemId;
            TenantContext.set(tenant.getId());
            try {
                itemId = transactionTemplate.execute(status ->
                        orderItemRepository.findByOrderIdOrderByOrderedAt(orderId).get(0).getId());
            } finally {
                TenantContext.clear();
            }

            mockMvc.perform(delete("/api/v1/orders/" + orderId + "/items/" + itemId)
                            .cookie(cookieFor(waiterB)))
                    .andExpect(status().isForbidden());

            assertThat(itemCount(orderId)).isEqualTo(1);
            assertThat(reload(orderId).getTotal()).isEqualByComparingTo(product.getPrice());
        }

        @Test
        @DisplayName("el dueno de la orden y el ADMIN si pueden operarla")
        void ownerAndAdminCanOperate() throws Exception {
            UUID orderId = openOrderAs(waiterA);
            addItemAs(waiterA, orderId);
            addItemAs(admin, orderId);

            mockMvc.perform(post("/api/v1/orders/" + orderId + "/send-to-kitchen")
                            .cookie(cookieFor(waiterA)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("SENT"));
        }
    }

    @Nested
    @DisplayName("Deficit 2: entradas sin limite (cantidad, notas, tamano del lote, comensales)")
    class InputLimits {

        @Test
        @DisplayName("una cantidad desmedida se rechaza con 400 y no altera el total")
        void hugeQuantityIsRejected() throws Exception {
            UUID orderId = openOrderAs(waiterA);

            mockMvc.perform(post("/api/v1/orders/" + orderId + "/items")
                            .cookie(cookieFor(waiterA))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(itemBody(Integer.MAX_VALUE)))
                    .andExpect(status().isBadRequest());

            assertThat(itemCount(orderId)).isZero();
        }

        @Test
        @DisplayName("una nota de mas de 500 caracteres se rechaza con 400")
        void oversizedNotesAreRejected() throws Exception {
            UUID orderId = openOrderAs(waiterA);
            String notes = "x".repeat(10_000);

            mockMvc.perform(post("/api/v1/orders/" + orderId + "/items")
                            .cookie(cookieFor(waiterA))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"items\":[{\"productId\":\"" + product.getId()
                                    + "\",\"quantity\":1,\"notes\":\"" + notes + "\"}]}"))
                    .andExpect(status().isBadRequest());

            assertThat(itemCount(orderId)).isZero();
        }

        @Test
        @DisplayName("un lote de mas de 50 lineas y una mesa de 10 000 comensales se rechazan")
        void oversizedBatchAndGuestCountAreRejected() throws Exception {
            UUID orderId = openOrderAs(waiterA);
            String line = "{\"productId\":\"" + product.getId() + "\",\"quantity\":1}";
            String batch = "{\"items\":[" + String.join(",", java.util.Collections.nCopies(51, line)) + "]}";

            mockMvc.perform(post("/api/v1/orders/" + orderId + "/items")
                            .cookie(cookieFor(waiterA))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(batch))
                    .andExpect(status().isBadRequest());
            assertThat(itemCount(orderId)).isZero();

            mockMvc.perform(post("/api/v1/orders/" + orderId + "/cancel").cookie(cookieFor(waiterA)))
                    .andExpect(status().isOk());
            mockMvc.perform(post("/api/v1/orders")
                            .cookie(cookieFor(waiterA))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"tableId\":\"" + table.getId() + "\",\"guestCount\":10000}"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("Deficit 3: un mesero cancela ordenes ya enviadas a cocina")
    class CancellationAfterKitchen {

        private UUID sentOrderOf(User user) throws Exception {
            UUID orderId = openOrderAs(user);
            addItemAs(user, orderId);
            mockMvc.perform(post("/api/v1/orders/" + orderId + "/send-to-kitchen")
                            .cookie(cookieFor(user)))
                    .andExpect(status().isOk());
            return orderId;
        }

        @Test
        @DisplayName("el mesero no puede cancelar su orden una vez enviada a cocina")
        void waiterCannotCancelASentOrder() throws Exception {
            UUID orderId = sentOrderOf(waiterA);

            mockMvc.perform(post("/api/v1/orders/" + orderId + "/cancel").cookie(cookieFor(waiterA)))
                    .andExpect(status().isForbidden());

            assertThat(reload(orderId).getStatus()).isEqualTo(OrderStatus.SENT);
            TenantContext.set(tenant.getId());
            try {
                RestaurantTable reloaded = transactionTemplate.execute(
                        status -> tableRepository.findById(table.getId()).orElseThrow());
                assertThat(reloaded.getStatus()).isEqualTo(TableStatus.OCCUPIED);
            } finally {
                TenantContext.clear();
            }
        }

        @Test
        @DisplayName("el mesero si puede cancelar su orden mientras sigue en NEW")
        void waiterCanCancelANewOrder() throws Exception {
            UUID orderId = openOrderAs(waiterA);

            mockMvc.perform(post("/api/v1/orders/" + orderId + "/cancel").cookie(cookieFor(waiterA)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("CANCELED"));
        }

        @Test
        @DisplayName("el ADMIN si puede cancelar una orden ya enviada a cocina")
        void adminCanCancelASentOrder() throws Exception {
            UUID orderId = sentOrderOf(waiterA);

            mockMvc.perform(post("/api/v1/orders/" + orderId + "/cancel").cookie(cookieFor(admin)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("CANCELED"));
        }
    }
}
