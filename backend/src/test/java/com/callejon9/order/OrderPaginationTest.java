package com.callejon9.order;

import com.callejon9.order.domain.Order;
import com.callejon9.order.domain.OrderStatus;
import com.callejon9.order.repository.OrderRepository;
import com.callejon9.platform.tenant.domain.Tenant;
import com.callejon9.platform.tenant.service.TenantOnboardingService;
import com.callejon9.support.TestSessions;
import com.callejon9.tenancy.TenantContext;
import com.callejon9.user.domain.User;
import com.callejon9.user.domain.UserRole;
import com.callejon9.user.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contrato paginado de {@code GET /api/v1/orders} (CAL-16): metadatos,
 * paginas sin huecos ni repetidos, filtro de abiertas, orden en allowlist,
 * limites de tamano y conteos acotados al tenant por RLS.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Paginacion de ordenes")
class OrderPaginationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private TenantOnboardingService onboardingService;
    @Autowired private TestSessions testSessions;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private UserRepository userRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private ObjectMapper objectMapper;

    private User waiter;
    /** Folios en orden de apertura: el primero es el mas antiguo. */
    private final List<String> folios = new ArrayList<>();

    @BeforeEach
    void seed() {
        Tenant tenant = onboardingService.onboard("Paginas", "paginas-test",
                "admin@paginas.com", "Admin", "Secreto123!", "FREE");
        waiter = TenantContext.callAs(tenant.getId(), () -> transactionTemplate.execute(status ->
                userRepository.save(User.builder().email("mesero@paginas.com").passwordHash("x")
                        .fullName("Mesero").role(UserRole.WAITER).active(true).build())));
        // 5 ordenes; dos ya fuera de servicio para probar el filtro de abiertas.
        // total = 50, 40, 30, 20, 10 en orden de apertura.
        OrderStatus[] statuses = {OrderStatus.NEW, OrderStatus.PAID, OrderStatus.SENT,
                OrderStatus.CANCELED, OrderStatus.READY};
        Instant base = Instant.now().truncatedTo(ChronoUnit.SECONDS).minus(1, ChronoUnit.HOURS);
        for (int i = 0; i < statuses.length; i++) {
            folios.add(seedOrder(tenant, "PAG-" + i, statuses[i], base.plus(i, ChronoUnit.MINUTES),
                    BigDecimal.valueOf(10L * (5 - i))));
        }

        Tenant other = onboardingService.onboard("Paginas B", "paginas-test-b",
                "admin@paginas-b.com", "Admin", "Secreto123!", "FREE");
        seedOrder(other, "PAG-B", OrderStatus.NEW, base, BigDecimal.ONE);
    }

    @AfterEach
    void cleanUp() {
        TenantContext.clear();
        jdbcTemplate.update("DELETE FROM tenants WHERE slug IN ('paginas-test','paginas-test-b')");
    }

    private String seedOrder(Tenant tenant, String folio, OrderStatus status, Instant openedAt, BigDecimal total) {
        TenantContext.callAs(tenant.getId(), () -> transactionTemplate.execute(tx -> orderRepository.save(
                Order.builder().folio(folio).guestCount(1).status(status).total(total).openedAt(openedAt).build())));
        return folio;
    }

    private Cookie waiterCookie() {
        return new Cookie("access_token", testSessions.accessTokenFor(waiter));
    }

    private JsonNode page(MockHttpServletRequestBuilder request) throws Exception {
        String body = mockMvc.perform(request.cookie(waiterCookie()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    private static List<String> foliosOf(JsonNode page) {
        List<String> result = new ArrayList<>();
        page.get("content").forEach(order -> result.add(order.get("folio").asText()));
        return result;
    }

    @Test
    @DisplayName("una pagina trae a lo mas size filas, con metadatos correctos y conteos solo del tenant")
    void pageIsBoundedWithCorrectMetadata() throws Exception {
        JsonNode first = page(get("/api/v1/orders").param("size", "2"));

        assertThat(first.get("content")).hasSize(2);
        assertThat(first.get("page").asInt()).isZero();
        assertThat(first.get("size").asInt()).isEqualTo(2);
        // 5 propias; la orden del otro restaurante no cuenta.
        assertThat(first.get("totalElements").asLong()).isEqualTo(5);
        assertThat(first.get("totalPages").asInt()).isEqualTo(3);
        // Por defecto, la mas reciente primero.
        assertThat(foliosOf(first)).containsExactly(folios.get(4), folios.get(3));

        JsonNode last = page(get("/api/v1/orders").param("size", "2").param("page", "2"));
        assertThat(foliosOf(last)).containsExactly(folios.get(0));

        JsonNode beyond = page(get("/api/v1/orders").param("size", "2").param("page", "7"));
        assertThat(beyond.get("content")).isEmpty();
        assertThat(beyond.get("totalElements").asLong()).isEqualTo(5);
    }

    @Test
    @DisplayName("recorrer todas las paginas devuelve cada orden exactamente una vez")
    void pagesHaveNoGapsOrDuplicates() throws Exception {
        List<String> seen = new ArrayList<>();
        for (int pageNumber = 0; pageNumber < 3; pageNumber++) {
            seen.addAll(foliosOf(page(get("/api/v1/orders").param("size", "2")
                    .param("page", String.valueOf(pageNumber)))));
        }

        assertThat(seen).hasSize(5).doesNotHaveDuplicates().containsExactlyInAnyOrderElementsOf(folios);
    }

    @Test
    @DisplayName("open=true deja solo NEW, SENT y READY")
    void openFilterKeepsOnlyOrdersInService() throws Exception {
        JsonNode open = page(get("/api/v1/orders").param("open", "true"));

        assertThat(open.get("totalElements").asLong()).isEqualTo(3);
        assertThat(foliosOf(open)).containsExactly(folios.get(4), folios.get(2), folios.get(0));
    }

    @Test
    @DisplayName("sort acepta campos de la allowlist en ambas direcciones")
    void sortsByAllowedFields() throws Exception {
        assertThat(foliosOf(page(get("/api/v1/orders").param("sort", "folio,asc"))))
                .containsExactlyElementsOf(folios);
        assertThat(foliosOf(page(get("/api/v1/orders").param("sort", "total,asc"))))
                .containsExactly(folios.get(4), folios.get(3), folios.get(2), folios.get(1), folios.get(0));
    }

    @ParameterizedTest(name = "{0}={1}")
    @CsvSource({"size,0", "size,101", "page,-1", "sort,waiterId", "sort,tenantId", "sort,'folio,up'"})
    @DisplayName("parametros de pagina fuera de lo permitido responden 400 con Problem Detail")
    void rejectsInvalidPageParameters(String name, String value) throws Exception {
        mockMvc.perform(get("/api/v1/orders").param(name, value).cookie(waiterCookie()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Paginacion invalida"))
                .andExpect(jsonPath("$.detail").isNotEmpty());
    }

    @Test
    @DisplayName("status y open juntos responden 400")
    void rejectsStatusTogetherWithOpen() throws Exception {
        mockMvc.perform(get("/api/v1/orders").param("status", "NEW").param("open", "true").cookie(waiterCookie()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Paginacion invalida"));
    }
}
