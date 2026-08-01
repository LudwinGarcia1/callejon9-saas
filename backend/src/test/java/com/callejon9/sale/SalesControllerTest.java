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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
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
 * Historial de ventas: una vez cobrada, una orden desaparece de las pantallas
 * operativas (mesero, cocina, caja), pero debe seguir siendo consultable
 * aqui. Reutiliza el mismo patron de fixtures que CheckoutControllerTest.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Historial de ventas")
class SalesControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private TenantOnboardingService onboardingService;
    @Autowired private JwtService jwtService;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private UserRepository userRepository;
    @Autowired private RestaurantTableRepository tableRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private ObjectMapper objectMapper;

    private Tenant tenant;
    private User waiter;
    private User cashier;
    private RestaurantTable table;

    @BeforeEach
    void seed() {
        tenant = onboardingService.onboard("Historial Test", "historial-test",
                "admin@historial.com", "Admin", "Secreto123!", "FREE");

        TenantContext.set(tenant.getId());
        try {
            waiter = transactionTemplate.execute(status -> userRepository.save(User.builder()
                    .email("mesero@historial.com").passwordHash("x").fullName("Mesero")
                    .role(UserRole.WAITER).active(true).build()));
            cashier = transactionTemplate.execute(status -> userRepository.save(User.builder()
                    .email("cajero@historial.com").passwordHash("x").fullName("Cajero Historial")
                    .role(UserRole.CASHIER).active(true).build()));
            table = transactionTemplate.execute(status -> tableRepository.save(RestaurantTable.builder()
                    .number(7).capacity(4).status(TableStatus.FREE).active(true).build()));
        } finally {
            TenantContext.clear();
        }
    }

    @AfterEach
    void cleanUp() {
        TenantContext.clear();
        jdbcTemplate.update("DELETE FROM tenants WHERE slug IN ('historial-test','historial-test-b')");
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

    /**
     * Abre una orden, agrega un producto y cobra; regresa el folio de la orden
     * (lo que la fila del historial debe mostrar) junto con el ticket emitido.
     */
    private CheckoutResult checkout(User asWaiter, User asCashier, RestaurantTable atTable, Product product,
            int quantity, String paymentMethod, int tipPercent) throws Exception {
        JsonNode order = openOrder(asWaiter, atTable);
        UUID orderId = UUID.fromString(order.get("id").asText());
        addItem(asWaiter, orderId, product, quantity);

        String ticketBody = mockMvc.perform(post("/api/v1/orders/" + orderId + "/checkout")
                        .cookie(cookieFor(asCashier))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentMethod\":\"" + paymentMethod + "\",\"tipPercent\":" + tipPercent + "}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return new CheckoutResult(order.get("folio").asText(), objectMapper.readTree(ticketBody));
    }

    /** Resultado de un cobro de prueba: el folio de la orden cobrada y el ticket que emitio. */
    private record CheckoutResult(String orderFolio, JsonNode ticket) {
    }

    /**
     * Retrasa el created_at de una venta a una fecha arbitraria (mediodia UTC
     * de ese dia). El UPDATE crudo necesita el tenant fijado en una
     * transaccion real: sin eso, RLS (V5__rls_policy_null_safe.sql) no revela
     * ninguna fila para un UPDATE crudo y la actualizacion afectaria cero
     * filas en silencio.
     */
    private void backdateSale(UUID saleId, LocalDate day) {
        backdateSaleToInstant(saleId, day.atTime(12, 0).toInstant(ZoneOffset.UTC));
    }

    /**
     * Igual que {@link #backdateSale(UUID, LocalDate)} pero recibe el
     * instante exacto, para probar limites de dia (primer/ultimo minuto)
     * en vez de mediodia.
     */
    private void backdateSaleToInstant(UUID saleId, Instant instant) {
        TenantContext.set(tenant.getId());
        try {
            transactionTemplate.executeWithoutResult(status -> jdbcTemplate.update(
                    "UPDATE sales SET created_at = ? WHERE id = ?",
                    java.sql.Timestamp.from(instant), saleId));
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    @DisplayName("sin parametros devuelve las ventas de hoy, mas reciente primero, con los datos de un vistazo")
    void defaultRangeReturnsTodaysSalesNewestFirstWithFullRowData() throws Exception {
        Product taco = createProduct("Taco", "25.00");
        Product agua = createProduct("Agua", "15.00");

        CheckoutResult first = checkout(waiter, cashier, table, taco, 1, "CASH", 10);
        CheckoutResult second = checkout(waiter, cashier, table, agua, 2, "CARD", 0);

        mockMvc.perform(get("/api/v1/sales").cookie(cookieFor(cashier)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sales.length()").value(2))
                // Mas reciente primero: la segunda venta (Agua) aparece antes que la primera (Taco).
                .andExpect(jsonPath("$.sales[0].id").value(second.ticket().get("saleId").asText()))
                .andExpect(jsonPath("$.sales[0].ticketId").value(second.ticket().get("id").asText()))
                .andExpect(jsonPath("$.sales[0].orderFolio").value(second.orderFolio()))
                .andExpect(jsonPath("$.sales[0].tableNumber").value(table.getNumber()))
                .andExpect(jsonPath("$.sales[0].cashierName").value("Cajero Historial"))
                .andExpect(jsonPath("$.sales[0].paymentMethod").value("CARD"))
                .andExpect(jsonPath("$.sales[0].subtotal").value(30.00))
                .andExpect(jsonPath("$.sales[0].tip").value(0.00))
                .andExpect(jsonPath("$.sales[0].total").value(30.00))
                .andExpect(jsonPath("$.sales[1].id").value(first.ticket().get("saleId").asText()))
                .andExpect(jsonPath("$.sales[1].ticketId").value(first.ticket().get("id").asText()))
                .andExpect(jsonPath("$.sales[1].orderFolio").value(first.orderFolio()))
                .andExpect(jsonPath("$.sales[1].subtotal").value(25.00))
                .andExpect(jsonPath("$.sales[1].tip").value(2.50))
                .andExpect(jsonPath("$.sales[1].total").value(27.50));
    }

    /**
     * El defecto, reproducido de forma determinista.
     *
     * Un dia del negocio va de medianoche a medianoche EN LA ZONA DEL
     * NEGOCIO. Con Mexico City en -06:00, las 23:30 del dia D locales son
     * las 05:30 UTC del dia D+1: resolviendo el rango en UTC esa venta cae
     * fuera del dia D, y la del ultimo minuto del dia D-1 cae dentro. El
     * historial termina mostrando la cena de anoche y escondiendo la de hoy.
     *
     * Este test afirma CUALES ventas vuelven, no cuantas. Contar no basta:
     * con la logica en UTC tambien vuelven dos filas, solo que son las
     * equivocadas, y una asercion de cantidad pasaria sin notarlo.
     *
     * Usa un dia fijo del pasado, asi que no depende de la hora a la que se
     * ejecute la suite.
     */
    @Test
    @DisplayName("un rango explicito cubre el dia completo en la zona del negocio, no en UTC")
    void explicitRangeCoversTheFullLocalDayNotTheUtcDay() throws Exception {
        Product taco = createProduct("Taco", "25.00");
        ZoneId businessZone = ZoneId.of("America/Mexico_City");
        LocalDate targetDay = LocalDate.now(businessZone).minusDays(10);

        // 23:30 locales del dia D. En UTC ya es D+1: con el bug, se pierde.
        CheckoutResult dinner = checkout(waiter, cashier, table, taco, 1, "CASH", 0);
        UUID dinnerSaleId = UUID.fromString(dinner.ticket().get("saleId").asText());
        backdateSaleToInstant(dinnerSaleId, targetDay.atTime(23, 30).atZone(businessZone).toInstant());

        // 23:30 locales del dia ANTERIOR. En UTC cae dentro de D: con el bug, se cuela.
        CheckoutResult previousNight = checkout(waiter, cashier, table, taco, 1, "CASH", 0);
        UUID previousNightSaleId = UUID.fromString(previousNight.ticket().get("saleId").asText());
        backdateSaleToInstant(previousNightSaleId,
                targetDay.minusDays(1).atTime(23, 30).atZone(businessZone).toInstant());

        mockMvc.perform(get("/api/v1/sales")
                        .param("from", targetDay.toString())
                        .param("to", targetDay.toString())
                        .cookie(cookieFor(cashier)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sales.length()").value(1))
                .andExpect(jsonPath("$.sales[0].id").value(dinnerSaleId.toString()))
                .andExpect(jsonPath("$.summary.count").value(1));
    }

    /**
     * El primer instante del dia local tambien tiene que entrar: 00:00:30
     * locales del dia D son las 06:00:30 UTC del mismo dia D, asi que este
     * caso pasa con ambas logicas. Se conserva igual para fijar el borde
     * inferior y detectar un arreglo que corra el rango de mas.
     */
    @Test
    @DisplayName("un rango explicito incluye el primer minuto del dia local")
    void explicitRangeIncludesTheFirstMinuteOfTheLocalDay() throws Exception {
        Product taco = createProduct("Taco", "25.00");
        ZoneId businessZone = ZoneId.of("America/Mexico_City");
        LocalDate targetDay = LocalDate.now(businessZone).minusDays(20);

        CheckoutResult firstMinute = checkout(waiter, cashier, table, taco, 1, "CASH", 0);
        UUID firstMinuteSaleId = UUID.fromString(firstMinute.ticket().get("saleId").asText());
        backdateSaleToInstant(firstMinuteSaleId, targetDay.atTime(0, 0, 30).atZone(businessZone).toInstant());

        mockMvc.perform(get("/api/v1/sales")
                        .param("from", targetDay.toString())
                        .param("to", targetDay.toString())
                        .cookie(cookieFor(cashier)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sales.length()").value(1))
                .andExpect(jsonPath("$.sales[0].id").value(firstMinuteSaleId.toString()));
    }

    @Test
    @DisplayName("un rango explicito solo trae las ventas dentro de esas fechas")
    void explicitRangeOnlyIncludesSalesWithinIt() throws Exception {
        Product taco = createProduct("Taco", "25.00");

        CheckoutResult old = checkout(waiter, cashier, table, taco, 1, "CASH", 0);
        UUID oldSaleId = UUID.fromString(old.ticket().get("saleId").asText());
        LocalDate tenDaysAgo = LocalDate.now(ZoneOffset.UTC).minusDays(10);
        backdateSale(oldSaleId, tenDaysAgo);

        CheckoutResult today = checkout(waiter, cashier, table, taco, 1, "CASH", 0);

        // Rango explicito que cubre solo la venta vieja.
        mockMvc.perform(get("/api/v1/sales")
                        .param("from", tenDaysAgo.toString())
                        .param("to", tenDaysAgo.toString())
                        .cookie(cookieFor(cashier)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sales.length()").value(1))
                .andExpect(jsonPath("$.sales[0].id").value(oldSaleId.toString()))
                .andExpect(jsonPath("$.summary.count").value(1))
                .andExpect(jsonPath("$.summary.total").value(25.00));

        // Sin parametros (hoy) solo debe traer la venta de hoy, no la vieja.
        mockMvc.perform(get("/api/v1/sales").cookie(cookieFor(cashier)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sales.length()").value(1))
                .andExpect(jsonPath("$.sales[0].id")
                        .value(today.ticket().get("saleId").asText()));
    }

    @Test
    @DisplayName("el resumen suma el total y cuenta las ventas del rango, no de una pagina de resultados")
    void summaryComputesCountAndTotalForTheWholeRange() throws Exception {
        Product taco = createProduct("Taco", "19.99");
        Product agua = createProduct("Agua", "10.01");

        checkout(waiter, cashier, table, taco, 1, "CASH", 0);
        checkout(waiter, cashier, table, agua, 1, "CARD", 0);
        checkout(waiter, cashier, table, taco, 2, "TRANSFER", 0);

        // 19.99 + 10.01 + (19.99*2) = 69.98
        mockMvc.perform(get("/api/v1/sales").cookie(cookieFor(cashier)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sales.length()").value(3))
                .andExpect(jsonPath("$.summary.count").value(3))
                .andExpect(jsonPath("$.summary.total").value(69.98));
    }

    @Test
    @DisplayName("una venta de otro restaurante nunca aparece en el historial")
    void crossTenantSaleNeverAppears() throws Exception {
        Product ownProduct = createProduct("Taco", "25.00");
        checkout(waiter, cashier, table, ownProduct, 1, "CASH", 0);

        Tenant tenantB = onboardingService.onboard("Historial Test B", "historial-test-b",
                "admin@historialb.com", "Admin B", "Secreto123!", "FREE");
        TenantContext.set(tenantB.getId());
        User waiterB;
        User cashierB;
        RestaurantTable tableB;
        Product productB;
        try {
            waiterB = transactionTemplate.execute(status -> userRepository.save(User.builder()
                    .email("mesero@historialb.com").passwordHash("x").fullName("Mesero B")
                    .role(UserRole.WAITER).active(true).build()));
            cashierB = transactionTemplate.execute(status -> userRepository.save(User.builder()
                    .email("cajero@historialb.com").passwordHash("x").fullName("Cajero B")
                    .role(UserRole.CASHIER).active(true).build()));
            tableB = transactionTemplate.execute(status -> tableRepository.save(RestaurantTable.builder()
                    .number(1).capacity(2).status(TableStatus.FREE).active(true).build()));
            productB = transactionTemplate.execute(status -> productRepository.save(Product.builder()
                    .name("Torta").description("Torta").price(new BigDecimal("40.00")).active(true).build()));
        } finally {
            TenantContext.clear();
        }
        checkout(waiterB, cashierB, tableB, productB, 1, "CASH", 0);

        // Tenant A: solo su propia venta, nunca la de B.
        mockMvc.perform(get("/api/v1/sales").cookie(cookieFor(cashier)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sales.length()").value(1))
                .andExpect(jsonPath("$.summary.count").value(1))
                .andExpect(jsonPath("$.summary.total").value(25.00));

        // Tenant B: solo la suya.
        mockMvc.perform(get("/api/v1/sales").cookie(cookieFor(cashierB)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sales.length()").value(1))
                .andExpect(jsonPath("$.summary.count").value(1))
                .andExpect(jsonPath("$.summary.total").value(40.00));
    }

    @Test
    @DisplayName("GET /api/v1/tickets?folio= encuentra el ticket por folio")
    void ticketCanBeFoundByFolio() throws Exception {
        Product taco = createProduct("Taco", "25.00");
        JsonNode ticket = checkout(waiter, cashier, table, taco, 1, "CASH", 0).ticket();
        String folio = ticket.get("folio").asText();

        mockMvc.perform(get("/api/v1/tickets").param("folio", folio).cookie(cookieFor(cashier)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ticket.get("id").asText()))
                .andExpect(jsonPath("$.folio").value(folio));
    }

    @Test
    @DisplayName("un folio que no existe en el restaurante da 404")
    void unknownFolioIsNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/tickets").param("folio", "TCK-000000000000").cookie(cookieFor(cashier)))
                .andExpect(status().isNotFound());
    }
}
