package com.callejon9.order;

import com.callejon9.platform.tenant.domain.Tenant;
import com.callejon9.platform.tenant.service.TenantOnboardingService;
import com.callejon9.support.TestSessions;
import com.callejon9.tenancy.TenantContext;
import com.callejon9.user.domain.UserRole;
import jakarta.servlet.http.Cookie;
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

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Menu QR contra PostgreSQL real. Las pruebas con mocks de
 * {@link PublicMenuControllerTest} no ven RLS: aqui se comprueba que el tenant
 * del slug llega a la transaccion y que un restaurante no ve el menu de otro.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Menu QR publico con RLS")
class PublicMenuIntegrationTest {

    private static final String SLUG_A = "qr-publico-a";
    private static final String SLUG_B = "qr-publico-b";

    @Autowired private MockMvc mockMvc;
    @Autowired private TenantOnboardingService onboardingService;
    @Autowired private TestSessions testSessions;
    @Autowired private JdbcTemplate jdbcTemplate;

    private Cookie adminA;
    private Cookie adminB;
    private String productA;

    @BeforeEach
    void seed() throws Exception {
        Tenant tenantA = onboardingService.onboard("QR Publico A", SLUG_A,
                "admin@qr-a.com", "Admin", "Secreto123!", "FREE");
        Tenant tenantB = onboardingService.onboard("QR Publico B", SLUG_B,
                "admin@qr-b.com", "Admin", "Secreto123!", "FREE");
        adminA = cookieFor(tenantA.getId());
        adminB = cookieFor(tenantB.getId());

        String categoryA = createAndReadId("/api/v1/categories", adminA, "{\"name\":\"Tacos\"}");
        productA = createAndReadId("/api/v1/products", adminA,
                "{\"name\":\"Taco al pastor\",\"price\":25.00,\"categoryId\":\"" + categoryA + "\"}");
        createAndReadId("/api/v1/tables", adminA, "{\"number\":1,\"capacity\":4}");

        createAndReadId("/api/v1/products", adminB, "{\"name\":\"Agua de horchata\",\"price\":18.00}");
    }

    @AfterEach
    void cleanUp() {
        TenantContext.clear();
        jdbcTemplate.update("DELETE FROM tenants WHERE slug IN (?, ?)", SLUG_A, SLUG_B);
    }

    @Test
    @DisplayName("el catalogo publico muestra las categorias y productos del restaurante del slug")
    void catalogShowsTenantData() throws Exception {
        mockMvc.perform(get("/api/v1/public/" + SLUG_A + "/catalog"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value(SLUG_A))
                .andExpect(jsonPath("$.categories", hasSize(1)))
                .andExpect(jsonPath("$.categories[0].name").value("Tacos"))
                .andExpect(jsonPath("$.products", hasSize(1)))
                .andExpect(jsonPath("$.products[0].name").value("Taco al pastor"));
    }

    @Test
    @DisplayName("el catalogo de un restaurante no incluye productos de otro")
    void catalogIsIsolatedPerTenant() throws Exception {
        mockMvc.perform(get("/api/v1/public/" + SLUG_B + "/catalog"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categories", hasSize(0)))
                .andExpect(jsonPath("$.products", hasSize(1)))
                .andExpect(jsonPath("$.products[0].name").value("Agua de horchata"));
    }

    @Test
    @DisplayName("el auto-pedido desde la mesa crea la comanda enviada a cocina")
    void tableOrderIsSentToKitchen() throws Exception {
        mockMvc.perform(post("/api/v1/public/" + SLUG_A + "/tables/1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"productId\":\"" + productA + "\",\"quantity\":2}]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SENT"))
                .andExpect(jsonPath("$.total").value(50.00))
                .andExpect(jsonPath("$.items", hasSize(1)));

        mockMvc.perform(get("/api/v1/orders").cookie(adminA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)));
    }

    @Test
    @DisplayName("un restaurante no puede pedir un producto de otro desde su mesa")
    void tableOrderRejectsForeignProduct() throws Exception {
        createAndReadId("/api/v1/tables", adminB, "{\"number\":1,\"capacity\":4}");

        mockMvc.perform(post("/api/v1/public/" + SLUG_B + "/tables/1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"productId\":\"" + productA + "\",\"quantity\":1}]}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("llamar al mesero responde con la mesa del restaurante del slug")
    void callWaiterFindsTenantTable() throws Exception {
        mockMvc.perform(post("/api/v1/public/" + SLUG_A + "/tables/1/call-waiter"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tableNumber").value(1));

        mockMvc.perform(post("/api/v1/public/" + SLUG_B + "/tables/1/call-waiter"))
                .andExpect(status().isNotFound());
    }

    private Cookie cookieFor(UUID tenantId) {
        return new Cookie("access_token",
                testSessions.accessTokenFor(testSessions.newUserIn(tenantId, UserRole.ADMIN)));
    }

    private String createAndReadId(String path, Cookie admin, String body) throws Exception {
        String response = mockMvc.perform(post(path)
                        .cookie(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return response.replaceAll(".*\"id\":\"([0-9a-fA-F-]+)\".*", "$1");
    }
}
