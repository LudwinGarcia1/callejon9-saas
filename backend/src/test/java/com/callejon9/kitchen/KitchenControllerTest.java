package com.callejon9.kitchen;

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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * El tablero de cocina: listar las ordenes SENT y avanzar el kitchenStatus de
 * cada producto. Sigue el mismo patron de fixtures que OrderControllerTest,
 * ya que necesita ordenes reales (waiter_id y table_id con FK a filas
 * existentes) antes de poder enviarlas a cocina.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Tablero de cocina")
class KitchenControllerTest {

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
    private User kitchenUser;

    @BeforeEach
    void seed() {
        tenant = onboardingService.onboard("Cocina Test", "cocina-test",
                "admin@cocina.com", "Admin", "Secreto123!", "FREE");

        TenantContext.set(tenant.getId());
        try {
            waiter = transactionTemplate.execute(status -> userRepository.save(User.builder()
                    .email("mesero@cocina.com").passwordHash("x").fullName("Mesero")
                    .role(UserRole.WAITER).active(true).build()));
            kitchenUser = transactionTemplate.execute(status -> userRepository.save(User.builder()
                    .email("cocinero@cocina.com").passwordHash("x").fullName("Cocinero")
                    .role(UserRole.KITCHEN).active(true).build()));
        } finally {
            TenantContext.clear();
        }
    }

    @AfterEach
    void cleanUp() {
        TenantContext.clear();
        jdbcTemplate.update("DELETE FROM tenants WHERE slug = 'cocina-test'");
    }

    private Cookie cookieFor(User user) {
        return new Cookie("access_token", jwtService.generateAccessToken(user));
    }

    private RestaurantTable createTable(int number) {
        TenantContext.set(tenant.getId());
        try {
            return transactionTemplate.execute(status -> tableRepository.save(RestaurantTable.builder()
                    .number(number).capacity(4).status(TableStatus.FREE).active(true).build()));
        } finally {
            TenantContext.clear();
        }
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

    private UUID openOrderOn(RestaurantTable table) throws Exception {
        String body = mockMvc.perform(post("/api/v1/orders")
                        .cookie(cookieFor(waiter))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tableId\":\"" + table.getId() + "\",\"guestCount\":2}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(body.replaceAll(".*\"id\":\"([0-9a-fA-F-]+)\".*", "$1"));
    }

    private void addItem(UUID orderId, Product product) throws Exception {
        mockMvc.perform(post("/api/v1/orders/" + orderId + "/items")
                        .cookie(cookieFor(waiter))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"productId\":\"" + product.getId() + "\",\"quantity\":1}]}"))
                .andExpect(status().isOk());
    }

    private void sendToKitchen(UUID orderId) throws Exception {
        mockMvc.perform(post("/api/v1/orders/" + orderId + "/send-to-kitchen").cookie(cookieFor(waiter)))
                .andExpect(status().isOk());
    }

    private UUID firstItemIdOf(UUID orderId) throws Exception {
        String body = mockMvc.perform(get("/api/v1/orders/" + orderId).cookie(cookieFor(waiter)))
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(body.replaceAll(".*\"items\":\\[\\{\"id\":\"([0-9a-fA-F-]+)\".*", "$1"));
    }

    @Test
    @DisplayName("GET /kitchen/orders lista las ordenes SENT, la mas antigua primero, con sus items en PENDING")
    void listsSentOrdersOldestFirst() throws Exception {
        Product product = createProduct("Taco", "25.00");

        UUID orderId1 = openOrderOn(createTable(1));
        addItem(orderId1, product);
        sendToKitchen(orderId1);

        UUID orderId2 = openOrderOn(createTable(2));
        addItem(orderId2, product);
        sendToKitchen(orderId2);

        mockMvc.perform(get("/api/v1/kitchen/orders").cookie(cookieFor(kitchenUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(orderId1.toString()))
                .andExpect(jsonPath("$[0].status").value("SENT"))
                .andExpect(jsonPath("$[0].items[0].kitchenStatus").value("PENDING"))
                .andExpect(jsonPath("$[1].id").value(orderId2.toString()));
    }

    @Test
    @DisplayName("WAITER no puede ver el tablero de cocina")
    void waiterCannotAccessTheKitchenBoard() throws Exception {
        mockMvc.perform(get("/api/v1/kitchen/orders").cookie(cookieFor(waiter)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("avanzar PENDING -> IN_PREPARATION es un movimiento legal")
    void legalForwardTransitionIsAccepted() throws Exception {
        Product product = createProduct("Taco", "25.00");
        UUID orderId = openOrderOn(createTable(1));
        addItem(orderId, product);
        sendToKitchen(orderId);
        UUID itemId = firstItemIdOf(orderId);

        mockMvc.perform(post("/api/v1/kitchen/items/" + itemId + "/status")
                        .cookie(cookieFor(kitchenUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"IN_PREPARATION\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kitchenStatus").value("IN_PREPARATION"));
    }

    @Test
    @DisplayName("brincarse un estado (PENDING -> READY) da 409")
    void skippingAStateIsRejected() throws Exception {
        Product product = createProduct("Taco", "25.00");
        UUID orderId = openOrderOn(createTable(1));
        addItem(orderId, product);
        sendToKitchen(orderId);
        UUID itemId = firstItemIdOf(orderId);

        mockMvc.perform(post("/api/v1/kitchen/items/" + itemId + "/status")
                        .cookie(cookieFor(kitchenUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"READY\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("retroceder un estado (READY -> PENDING) da 409")
    void movingBackwardsIsRejected() throws Exception {
        Product product = createProduct("Taco", "25.00");
        UUID orderId = openOrderOn(createTable(1));
        addItem(orderId, product);
        sendToKitchen(orderId);
        UUID itemId = firstItemIdOf(orderId);

        mockMvc.perform(post("/api/v1/kitchen/items/" + itemId + "/status")
                        .cookie(cookieFor(kitchenUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"IN_PREPARATION\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/kitchen/items/" + itemId + "/status")
                        .cookie(cookieFor(kitchenUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"READY\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/kitchen/items/" + itemId + "/status")
                        .cookie(cookieFor(kitchenUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PENDING\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("cuando todos los items de una orden llegan a READY, la orden pasa a READY sola")
    void orderBecomesReadyWhenEveryItemIsReady() throws Exception {
        Product taco = createProduct("Taco", "25.00");
        Product quesadilla = createProduct("Quesadilla", "35.00");

        UUID orderId = openOrderOn(createTable(1));
        addItem(orderId, taco);
        addItem(orderId, quesadilla);
        sendToKitchen(orderId);

        String body = mockMvc.perform(get("/api/v1/orders/" + orderId).cookie(cookieFor(waiter)))
                .andReturn().getResponse().getContentAsString();
        var itemIds = java.util.regex.Pattern.compile("\"id\":\"([0-9a-fA-F-]+)\"")
                .matcher(body.substring(body.indexOf("\"items\"")))
                .results()
                .map(m -> m.group(1))
                .toList();

        for (String itemId : itemIds) {
            advance(itemId, "IN_PREPARATION");
            advance(itemId, "READY");
        }

        mockMvc.perform(get("/api/v1/orders/" + orderId).cookie(cookieFor(waiter)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READY"));
    }

    private void advance(String itemId, String newStatus) throws Exception {
        mockMvc.perform(post("/api/v1/kitchen/items/" + itemId + "/status")
                        .cookie(cookieFor(kitchenUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"" + newStatus + "\"}"))
                .andExpect(status().isOk());
    }
}
