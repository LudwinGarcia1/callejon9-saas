package com.callejon9.analytics;

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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
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
 * Pantalla de analitica: reconecta CRISP-DM (Pareto de productos,
 * distribucion de ventas por dia, mezcla de pago) con datos reales. Reutiliza
 * el mismo patron de fixtures que SalesControllerTest.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Analitica")
class AnalyticsControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private TenantOnboardingService onboardingService;
    @Autowired private JwtService jwtService;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private UserRepository userRepository;
    @Autowired private RestaurantTableRepository tableRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private ObjectMapper objectMapper;

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("America/Mexico_City");

    private Tenant tenant;
    private User waiter;
    private User cashier;
    private RestaurantTable table;

    @BeforeEach
    void seed() {
        tenant = onboardingService.onboard("Analitica Test", "analitica-test",
                "admin@analitica.com", "Admin", "Secreto123!", "FREE");

        TenantContext.set(tenant.getId());
        try {
            waiter = transactionTemplate.execute(status -> userRepository.save(User.builder()
                    .email("mesero@analitica.com").passwordHash("x").fullName("Mesero")
                    .role(UserRole.WAITER).active(true).build()));
            cashier = transactionTemplate.execute(status -> userRepository.save(User.builder()
                    .email("cajero@analitica.com").passwordHash("x").fullName("Cajero")
                    .role(UserRole.CASHIER).active(true).build()));
            table = transactionTemplate.execute(status -> tableRepository.save(RestaurantTable.builder()
                    .number(9).capacity(4).status(TableStatus.FREE).active(true).build()));
        } finally {
            TenantContext.clear();
        }
    }

    @AfterEach
    void cleanUp() {
        TenantContext.clear();
        jdbcTemplate.update("DELETE FROM tenants WHERE slug IN ('analitica-test','analitica-test-b')");
    }

    private Cookie cookieFor(User user) {
        return new Cookie("access_token", jwtService.generateAccessToken(user));
    }

    private Product createProduct(Tenant owner, String name, String price) {
        TenantContext.set(owner.getId());
        try {
            return transactionTemplate.execute(status -> productRepository.save(Product.builder()
                    .name(name).description(name).price(new BigDecimal(price)).active(true).build()));
        } finally {
            TenantContext.clear();
        }
    }

    private JsonNode openOrder(User asWaiter, RestaurantTable atTable) throws Exception {
        String body = mockMvc.perform(post("/api/v1/orders")
                        .cookie(cookieFor(asWaiter))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tableId\":\"" + atTable.getId() + "\",\"guestCount\":2}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    private void addItem(User asWaiter, UUID orderId, Product product, int quantity) throws Exception {
        mockMvc.perform(post("/api/v1/orders/" + orderId + "/items")
                        .cookie(cookieFor(asWaiter))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"productId\":\"" + product.getId()
                                + "\",\"quantity\":" + quantity + "}]}"))
                .andExpect(status().isOk());
    }

    /** Abre una orden, agrega un producto y cobra; regresa el id de la venta generada. */
    private UUID checkout(User asWaiter, User asCashier, RestaurantTable atTable, Product product,
            int quantity, String paymentMethod) throws Exception {
        JsonNode order = openOrder(asWaiter, atTable);
        UUID orderId = UUID.fromString(order.get("id").asText());
        addItem(asWaiter, orderId, product, quantity);

        String ticketBody = mockMvc.perform(post("/api/v1/orders/" + orderId + "/checkout")
                        .cookie(cookieFor(asCashier))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentMethod\":\"" + paymentMethod + "\",\"tipPercent\":0}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(ticketBody).get("saleId").asText());
    }

    /**
     * Recorre {@code sale_id} para fijar {@code created_at} a mediodia (zona
     * del negocio) del dia dado. Requiere el tenant fijado en una transaccion
     * real: sin eso, RLS no revela ninguna fila para un UPDATE crudo y la
     * actualizacion afectaria cero filas en silencio.
     */
    private void backdateSale(Tenant owner, UUID saleId, LocalDate day) {
        TenantContext.set(owner.getId());
        try {
            transactionTemplate.executeWithoutResult(status -> jdbcTemplate.update(
                    "UPDATE sales SET created_at = ? WHERE id = ?",
                    java.sql.Timestamp.from(day.atTime(12, 0).atZone(BUSINESS_ZONE).toInstant()),
                    saleId));
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    @DisplayName("las participaciones del Pareto suman 100 y el acumulado llega a 100")
    void paretoSharesSumToHundredAndCumulativeReachesHundred() throws Exception {
        Product taco = createProduct(tenant, "Taco", "25.00");
        Product agua = createProduct(tenant, "Agua", "15.00");
        Product torta = createProduct(tenant, "Torta", "40.00");

        LocalDate day = LocalDate.now(BUSINESS_ZONE).minusDays(1);
        backdateSale(tenant, checkout(waiter, cashier, table, taco, 3, "CASH"), day);
        backdateSale(tenant, checkout(waiter, cashier, table, agua, 2, "CARD"), day);
        backdateSale(tenant, checkout(waiter, cashier, table, torta, 1, "TRANSFER"), day);

        String body = mockMvc.perform(get("/api/v1/analytics")
                        .param("from", day.toString())
                        .param("to", day.toString())
                        .cookie(cookieFor(cashier)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pareto.length()").value(3))
                .andReturn().getResponse().getContentAsString();

        JsonNode pareto = objectMapper.readTree(body).get("pareto");
        BigDecimal shareSum = BigDecimal.ZERO;
        for (JsonNode row : pareto) {
            shareSum = shareSum.add(row.get("revenueShare").decimalValue());
        }
        org.junit.jupiter.api.Assertions.assertEquals(0, shareSum.compareTo(new BigDecimal("100.00")));

        JsonNode lastRow = pareto.get(pareto.size() - 1);
        org.junit.jupiter.api.Assertions.assertEquals(
                0, lastRow.get("cumulativeShare").decimalValue().compareTo(new BigDecimal("100.00")));
        // Ordenado descendente por ingreso: Taco (75.00) primero.
        org.junit.jupiter.api.Assertions.assertEquals("Taco", pareto.get(0).get("productName").asText());
    }

    @Test
    @DisplayName("mas de 12 productos se pliegan en una fila Otros, sin perder ingreso")
    void paretoFoldsProductsBeyondTwelveIntoOthers() throws Exception {
        LocalDate day = LocalDate.now(BUSINESS_ZONE).minusDays(2);
        BigDecimal totalRevenue = BigDecimal.ZERO;
        for (int i = 1; i <= 14; i++) {
            Product product = createProduct(tenant, "Producto " + i, (i + 1) + ".00");
            UUID saleId = checkout(waiter, cashier, table, product, 1, "CASH");
            backdateSale(tenant, saleId, day);
            totalRevenue = totalRevenue.add(new BigDecimal((i + 1) + ".00"));
        }

        String body = mockMvc.perform(get("/api/v1/analytics")
                        .param("from", day.toString())
                        .param("to", day.toString())
                        .cookie(cookieFor(cashier)))
                .andExpect(status().isOk())
                // 12 productos + la fila "Otros".
                .andExpect(jsonPath("$.pareto.length()").value(13))
                .andExpect(jsonPath("$.pareto[12].productName").value("Otros"))
                .andReturn().getResponse().getContentAsString();

        JsonNode pareto = objectMapper.readTree(body).get("pareto");
        BigDecimal sumOfRevenues = BigDecimal.ZERO;
        for (JsonNode row : pareto) {
            sumOfRevenues = sumOfRevenues.add(row.get("revenue").decimalValue());
        }
        org.junit.jupiter.api.Assertions.assertEquals(0, sumOfRevenues.compareTo(totalRevenue));
    }

    @Test
    @DisplayName("un dia sin ventas aparece igual en salesByDay, con total y conteo en cero")
    void salesByDayIncludesDayWithNoSalesAsZero() throws Exception {
        Product taco = createProduct(tenant, "Taco", "25.00");
        LocalDate firstDay = LocalDate.now(BUSINESS_ZONE).minusDays(5);
        LocalDate emptyDay = firstDay.plusDays(1);
        LocalDate lastDay = firstDay.plusDays(2);

        backdateSale(tenant, checkout(waiter, cashier, table, taco, 1, "CASH"), firstDay);
        backdateSale(tenant, checkout(waiter, cashier, table, taco, 2, "CASH"), lastDay);

        String body = mockMvc.perform(get("/api/v1/analytics")
                        .param("from", firstDay.toString())
                        .param("to", lastDay.toString())
                        .cookie(cookieFor(cashier)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.salesByDay.length()").value(3))
                .andExpect(jsonPath("$.salesByDay[0].day").value(firstDay.toString()))
                .andExpect(jsonPath("$.salesByDay[0].count").value(1))
                .andExpect(jsonPath("$.salesByDay[1].day").value(emptyDay.toString()))
                .andExpect(jsonPath("$.salesByDay[1].count").value(0))
                .andExpect(jsonPath("$.salesByDay[1].total").value(0))
                .andExpect(jsonPath("$.salesByDay[2].day").value(lastDay.toString()))
                .andExpect(jsonPath("$.salesByDay[2].count").value(1))
                .andReturn().getResponse().getContentAsString();

        org.junit.jupiter.api.Assertions.assertTrue(body.contains(emptyDay.toString()));
    }

    @Test
    @DisplayName("la mezcla de pago agrupa conteo e ingreso por metodo")
    void paymentMixGroupsCountAndTotalByMethod() throws Exception {
        Product taco = createProduct(tenant, "Taco", "20.00");
        LocalDate day = LocalDate.now(BUSINESS_ZONE).minusDays(3);

        backdateSale(tenant, checkout(waiter, cashier, table, taco, 1, "CASH"), day);
        backdateSale(tenant, checkout(waiter, cashier, table, taco, 1, "CASH"), day);
        backdateSale(tenant, checkout(waiter, cashier, table, taco, 1, "CARD"), day);

        mockMvc.perform(get("/api/v1/analytics")
                        .param("from", day.toString())
                        .param("to", day.toString())
                        .cookie(cookieFor(cashier)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentMix.length()").value(2))
                .andExpect(jsonPath("$.paymentMix[0].method").value("CASH"))
                .andExpect(jsonPath("$.paymentMix[0].count").value(2))
                .andExpect(jsonPath("$.paymentMix[0].total").value(40.00))
                .andExpect(jsonPath("$.paymentMix[1].method").value("CARD"))
                .andExpect(jsonPath("$.paymentMix[1].count").value(1))
                .andExpect(jsonPath("$.paymentMix[1].total").value(20.00));
    }

    @Test
    @DisplayName("sin parametros, el rango por defecto son los ultimos 7 dias terminando hoy")
    void defaultRangeIsLastSevenDaysEndingToday() throws Exception {
        Product taco = createProduct(tenant, "Taco", "10.00");

        // Dentro del rango por defecto (hoy - 6).
        UUID insideRange = checkout(waiter, cashier, table, taco, 1, "CASH");
        backdateSale(tenant, insideRange, LocalDate.now(BUSINESS_ZONE).minusDays(6));

        // Fuera del rango por defecto.
        UUID outsideRange = checkout(waiter, cashier, table, taco, 1, "CASH");
        backdateSale(tenant, outsideRange, LocalDate.now(BUSINESS_ZONE).minusDays(7));

        mockMvc.perform(get("/api/v1/analytics").cookie(cookieFor(cashier)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.salesByDay.length()").value(7))
                .andExpect(jsonPath("$.pareto[0].revenue").value(10.00))
                .andExpect(jsonPath("$.paymentMix[0].count").value(1));
    }

    @Test
    @DisplayName("una venta de otro restaurante nunca aporta a la analitica")
    void crossTenantSaleNeverContributes() throws Exception {
        Product ownProduct = createProduct(tenant, "Taco", "25.00");
        LocalDate day = LocalDate.now(BUSINESS_ZONE).minusDays(1);
        backdateSale(tenant, checkout(waiter, cashier, table, ownProduct, 1, "CASH"), day);

        Tenant tenantB = onboardingService.onboard("Analitica Test B", "analitica-test-b",
                "admin@analiticab.com", "Admin B", "Secreto123!", "FREE");
        TenantContext.set(tenantB.getId());
        User waiterB;
        User cashierB;
        RestaurantTable tableB;
        try {
            waiterB = transactionTemplate.execute(status -> userRepository.save(User.builder()
                    .email("mesero@analiticab.com").passwordHash("x").fullName("Mesero B")
                    .role(UserRole.WAITER).active(true).build()));
            cashierB = transactionTemplate.execute(status -> userRepository.save(User.builder()
                    .email("cajero@analiticab.com").passwordHash("x").fullName("Cajero B")
                    .role(UserRole.CASHIER).active(true).build()));
            tableB = transactionTemplate.execute(status -> tableRepository.save(RestaurantTable.builder()
                    .number(1).capacity(2).status(TableStatus.FREE).active(true).build()));
        } finally {
            TenantContext.clear();
        }
        Product productB = createProduct(tenantB, "Torta Ahogada", "99.00");
        backdateSale(tenantB, checkout(waiterB, cashierB, tableB, productB, 5, "MERCADOPAGO"), day);

        // Tenant A: solo su propio producto y su propio ingreso.
        mockMvc.perform(get("/api/v1/analytics")
                        .param("from", day.toString())
                        .param("to", day.toString())
                        .cookie(cookieFor(cashier)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pareto.length()").value(1))
                .andExpect(jsonPath("$.pareto[0].productName").value("Taco"))
                .andExpect(jsonPath("$.pareto[0].revenue").value(25.00))
                .andExpect(jsonPath("$.salesByDay[0].total").value(25.00))
                .andExpect(jsonPath("$.paymentMix.length()").value(1))
                .andExpect(jsonPath("$.paymentMix[0].method").value("CASH"));

        // Tenant B: solo el suyo.
        mockMvc.perform(get("/api/v1/analytics")
                        .param("from", day.toString())
                        .param("to", day.toString())
                        .cookie(cookieFor(cashierB)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pareto.length()").value(1))
                .andExpect(jsonPath("$.pareto[0].productName").value("Torta Ahogada"))
                .andExpect(jsonPath("$.pareto[0].revenue").value(495.00))
                .andExpect(jsonPath("$.paymentMix[0].method").value("MERCADOPAGO"));
    }
}
