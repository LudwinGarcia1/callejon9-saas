package com.callejon9.order;

import com.callejon9.auth.service.JwtService;
import com.callejon9.catalog.domain.Product;
import com.callejon9.catalog.repository.ProductRepository;
import com.callejon9.order.domain.OrderStatus;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * El flujo de meseros: abrir una orden, agregar productos y enviarla a
 * cocina. El waiter_id de orders y restaurant_tables tiene FK a users, asi
 * que a diferencia de TenantFilterTest (que solo prueba el filtro de
 * seguridad) aqui el usuario autenticado debe existir realmente en la tabla
 * users del tenant.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Ordenes de mesero")
class OrderControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private TenantOnboardingService onboardingService;
    @Autowired private JwtService jwtService;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private UserRepository userRepository;
    @Autowired private RestaurantTableRepository tableRepository;
    @Autowired private ProductRepository productRepository;

    private Tenant tenant;
    private User admin;
    private User waiter;
    private RestaurantTable table;
    private Product product;

    @BeforeEach
    void seed() {
        tenant = onboardingService.onboard("Ordenes Test", "ordenes-test",
                "admin@ordenes.com", "Admin", "Secreto123!", "FREE");

        TenantContext.set(tenant.getId());
        try {
            admin = transactionTemplate.execute(
                    status -> userRepository.findByEmail("admin@ordenes.com").orElseThrow());

            waiter = transactionTemplate.execute(status -> userRepository.save(User.builder()
                    .email("mesero@ordenes.com").passwordHash("x").fullName("Mesero")
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
        jdbcTemplate.update("DELETE FROM tenants WHERE slug IN ('ordenes-test','ordenes-test-b')");
    }

    private Cookie cookieFor(User user) {
        return new Cookie("access_token", jwtService.generateAccessToken(user));
    }

    @Test
    @DisplayName("un WAITER abre una orden y la mesa queda OCCUPIED")
    void waiterOpensAnOrder() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                        .cookie(cookieFor(waiter))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tableId\":\"" + table.getId() + "\",\"guestCount\":2}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("NEW"))
                .andExpect(jsonPath("$.folio").value(org.hamcrest.Matchers.matchesPattern("ORD-\\d{12}")))
                .andExpect(jsonPath("$.total").value(0));

        TenantContext.set(tenant.getId());
        try {
            RestaurantTable reloaded = transactionTemplate.execute(
                    status -> tableRepository.findById(table.getId()).orElseThrow());
            assertThat(reloaded.getStatus()).isEqualTo(TableStatus.OCCUPIED);
            assertThat(reloaded.getWaiterId()).isEqualTo(waiter.getId());
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    @DisplayName("abrir una orden sobre una mesa ya ocupada da 409")
    void cannotOpenAnOrderOnAnOccupiedTable() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                        .cookie(cookieFor(waiter))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tableId\":\"" + table.getId() + "\",\"guestCount\":2}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/orders")
                        .cookie(cookieFor(waiter))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tableId\":\"" + table.getId() + "\",\"guestCount\":1}"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("una mesa dada de baja no se puede abrir para una orden nueva")
    void cannotOpenAnOrderOnADeactivatedTable() throws Exception {
        mockMvc.perform(patch("/api/v1/tables/" + table.getId())
                        .cookie(cookieFor(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        mockMvc.perform(post("/api/v1/orders")
                        .cookie(cookieFor(waiter))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tableId\":\"" + table.getId() + "\",\"guestCount\":2}"))
                .andExpect(status().isConflict());
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

    @Test
    @DisplayName("agregar productos toma una foto del precio y recalcula el total")
    void addingItemsSnapshotsThePriceAndRecomputesTheTotal() throws Exception {
        UUID orderId = openOrder();

        mockMvc.perform(post("/api/v1/orders/" + orderId + "/items")
                        .cookie(cookieFor(waiter))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"productId\":\"" + product.getId()
                                + "\",\"quantity\":2,\"notes\":\"sin cebolla\"}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(50.00))
                .andExpect(jsonPath("$.items[0].productName").value("Taco"))
                .andExpect(jsonPath("$.items[0].unitPrice").value(25.00))
                .andExpect(jsonPath("$.items[0].notes").value("sin cebolla"));

        // El precio del producto sube DESPUES de agregarlo a la orden.
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

        mockMvc.perform(get("/api/v1/orders/" + orderId).cookie(cookieFor(waiter)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].unitPrice").value(25.00))
                .andExpect(jsonPath("$.total").value(50.00));
    }

    @Test
    @DisplayName("cambiar el precio de un producto por PUT no afecta las ordenes ya abiertas")
    void updatingAProductPriceThroughThePutEndpointDoesNotAffectAlreadyPlacedOrders() throws Exception {
        UUID orderId = openOrder();
        mockMvc.perform(post("/api/v1/orders/" + orderId + "/items")
                        .cookie(cookieFor(waiter))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"productId\":\"" + product.getId()
                                + "\",\"quantity\":1}]}"))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/products/" + product.getId())
                        .cookie(cookieFor(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Taco","description":"Taco al pastor","price":99.00}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.price").value(99.00));

        mockMvc.perform(get("/api/v1/orders/" + orderId).cookie(cookieFor(waiter)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].unitPrice").value(25.00))
                .andExpect(jsonPath("$.total").value(25.00));
    }

    @Test
    @DisplayName("dar de baja un producto no afecta las ordenes que ya lo incluyen; "
            + "solo desaparece del catalogo")
    void deactivatingAProductDoesNotAffectExistingOrdersOnlyHidesItFromTheCatalog() throws Exception {
        UUID orderId = openOrder();
        mockMvc.perform(post("/api/v1/orders/" + orderId + "/items")
                        .cookie(cookieFor(waiter))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"productId\":\"" + product.getId()
                                + "\",\"quantity\":1}]}"))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/v1/products/" + product.getId())
                        .cookie(cookieFor(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        mockMvc.perform(get("/api/v1/products").cookie(cookieFor(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(get("/api/v1/orders/" + orderId).cookie(cookieFor(waiter)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].productName").value("Taco"))
                .andExpect(jsonPath("$.items[0].unitPrice").value(25.00))
                .andExpect(jsonPath("$.total").value(25.00));
    }

    @Test
    @DisplayName("agregar productos a una orden PAID o CANCELED da 409")
    void cannotAddItemsToAClosedOrder() throws Exception {
        UUID orderId = openOrder();

        TenantContext.set(tenant.getId());
        try {
            transactionTemplate.executeWithoutResult(status ->
                    jdbcTemplate.update("UPDATE orders SET status = 'CANCELED' WHERE id = ?", orderId));
        } finally {
            TenantContext.clear();
        }

        mockMvc.perform(post("/api/v1/orders/" + orderId + "/items")
                        .cookie(cookieFor(waiter))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"productId\":\"" + product.getId()
                                + "\",\"quantity\":1}]}"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("enviar a cocina cambia el estado a SENT y marca sentToKitchenAt")
    void sendToKitchenTransitionsTheOrder() throws Exception {
        UUID orderId = openOrder();
        mockMvc.perform(post("/api/v1/orders/" + orderId + "/items")
                        .cookie(cookieFor(waiter))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"productId\":\"" + product.getId()
                                + "\",\"quantity\":1}]}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/orders/" + orderId + "/send-to-kitchen")
                        .cookie(cookieFor(waiter)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SENT"))
                .andExpect(jsonPath("$.sentToKitchenAt").isNotEmpty())
                .andExpect(jsonPath("$.items[0].kitchenStatus").value("PENDING"));
    }

    @Test
    @DisplayName("enviar a cocina una orden sin productos da 409")
    void sendToKitchenWithoutItemsIsRejected() throws Exception {
        UUID orderId = openOrder();

        mockMvc.perform(post("/api/v1/orders/" + orderId + "/send-to-kitchen")
                        .cookie(cookieFor(waiter)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("enviar a cocina una orden que no esta NEW da 409")
    void sendToKitchenTwiceIsRejected() throws Exception {
        UUID orderId = openOrder();
        mockMvc.perform(post("/api/v1/orders/" + orderId + "/items")
                        .cookie(cookieFor(waiter))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"productId\":\"" + product.getId()
                                + "\",\"quantity\":1}]}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/orders/" + orderId + "/send-to-kitchen")
                        .cookie(cookieFor(waiter)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/orders/" + orderId + "/send-to-kitchen")
                        .cookie(cookieFor(waiter)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("GET /orders?status= filtra por estado")
    void listFiltersByStatus() throws Exception {
        openOrder();

        mockMvc.perform(get("/api/v1/orders").param("status", "NEW").cookie(cookieFor(waiter)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].status").value("NEW"));

        mockMvc.perform(get("/api/v1/orders").param("status", "PAID").cookie(cookieFor(waiter)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("cancelar una orden la marca CANCELED, libera la mesa y conserva sus lineas")
    void cancelingAnOrderFreesTheTableAndKeepsItsItems() throws Exception {
        UUID orderId = openOrder();
        mockMvc.perform(post("/api/v1/orders/" + orderId + "/items")
                        .cookie(cookieFor(waiter))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"productId\":\"" + product.getId()
                                + "\",\"quantity\":1}]}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/orders/" + orderId + "/cancel").cookie(cookieFor(waiter)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELED"))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].productName").value("Taco"));

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
    @DisplayName("una mesa liberada por cancelacion se puede volver a ocupar")
    void aTableFreedByCancellationCanBeOccupiedAgain() throws Exception {
        UUID orderId = openOrder();
        mockMvc.perform(post("/api/v1/orders/" + orderId + "/cancel").cookie(cookieFor(waiter)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/orders")
                        .cookie(cookieFor(waiter))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tableId\":\"" + table.getId() + "\",\"guestCount\":3}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("NEW"));
    }

    @Test
    @DisplayName("cancelar una orden ya PAID o ya CANCELED da 409")
    void cancelingAClosedOrderIsRejected() throws Exception {
        UUID orderId = openOrder();

        TenantContext.set(tenant.getId());
        try {
            transactionTemplate.executeWithoutResult(status ->
                    jdbcTemplate.update("UPDATE orders SET status = 'PAID' WHERE id = ?", orderId));
        } finally {
            TenantContext.clear();
        }

        mockMvc.perform(post("/api/v1/orders/" + orderId + "/cancel").cookie(cookieFor(waiter)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("cancelar una orden dos veces da 409 la segunda vez")
    void doubleCancellationIsRejected() throws Exception {
        UUID orderId = openOrder();

        mockMvc.perform(post("/api/v1/orders/" + orderId + "/cancel").cookie(cookieFor(waiter)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/orders/" + orderId + "/cancel").cookie(cookieFor(waiter)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("cancelar una orden que no existe da 404")
    void cancelingANonexistentOrderGives404() throws Exception {
        mockMvc.perform(post("/api/v1/orders/" + UUID.randomUUID() + "/cancel")
                        .cookie(cookieFor(waiter)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("aislamiento entre tenants: listar ordenes de A nunca revela ordenes de B")
    void crossTenantIsolationHoldsForOrders() throws Exception {
        UUID orderIdA = openOrder();

        Tenant tenantB = onboardingService.onboard("Ordenes Test B", "ordenes-test-b",
                "admin@ordenesb.com", "Admin B", "Secreto123!", "FREE");

        TenantContext.set(tenantB.getId());
        User waiterB;
        RestaurantTable tableB;
        try {
            waiterB = transactionTemplate.execute(status -> userRepository.save(User.builder()
                    .email("mesero@ordenesb.com").passwordHash("x").fullName("Mesero B")
                    .role(UserRole.WAITER).active(true).build()));
            tableB = transactionTemplate.execute(status -> tableRepository.save(RestaurantTable.builder()
                    .number(1).capacity(2).status(TableStatus.FREE).active(true).build()));
        } finally {
            TenantContext.clear();
        }

        String bodyB = mockMvc.perform(post("/api/v1/orders")
                        .cookie(cookieFor(waiterB))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tableId\":\"" + tableB.getId() + "\",\"guestCount\":2}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID orderIdB = UUID.fromString(bodyB.replaceAll(".*\"id\":\"([0-9a-fA-F-]+)\".*", "$1"));

        // Tenant A: la lista de ordenes nunca debe contener la orden de B.
        mockMvc.perform(get("/api/v1/orders").cookie(cookieFor(waiter)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(orderIdA.toString()));

        // Y tampoco puede leerla por id: RLS la oculta, asi que responde 404.
        mockMvc.perform(get("/api/v1/orders/" + orderIdB).cookie(cookieFor(waiter)))
                .andExpect(status().isNotFound());

        assertThat(orderIdA).isNotEqualTo(orderIdB);
    }
}
