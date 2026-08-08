package com.callejon9.inventory;

import com.callejon9.auth.service.JwtService;
import com.callejon9.platform.tenant.domain.Tenant;
import com.callejon9.platform.tenant.service.TenantOnboardingService;
import com.callejon9.tenancy.TenantContext;
import com.callejon9.user.domain.User;
import com.callejon9.user.domain.UserRole;
import com.callejon9.user.repository.UserRepository;
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
import org.springframework.transaction.support.TransactionTemplate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Los usuarios se persisten de verdad, no se fabrican en memoria:
 * inventory_movements.user_id tiene FK a users, asi que un principal inventado
 * hace fallar el INSERT del movimiento. Mismo motivo que en
 * CheckoutControllerTest con waiter_id y cashier_id.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Movimientos de inventario")
class InventoryMovementControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private TenantOnboardingService onboardingService;
    @Autowired private JwtService jwtService;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private UserRepository userRepository;

    private Tenant tenant;
    private User admin;

    @BeforeEach
    void seed() {
        tenant = onboardingService.onboard("Movimientos Test", "movimientos-test",
                "admin@movimientos.com", "Admin", "Secreto123!", "FREE");
        admin = persistedUser(UserRole.ADMIN);
    }

    @AfterEach
    void cleanUp() {
        TenantContext.clear();
        jdbcTemplate.update("DELETE FROM tenants WHERE slug = 'movimientos-test'");
    }

    private Cookie cookieFor(User user) {
        return new Cookie("access_token", jwtService.generateAccessToken(user));
    }

    /**
     * El correo lleva un dominio propio para no chocar con el admin que ya
     * creo el onboarding. El nombre completo es el del rol porque el listado
     * de movimientos lo muestra como autor.
     */
    private User persistedUser(UserRole role) {
        TenantContext.set(tenant.getId());
        try {
            return transactionTemplate.execute(status -> userRepository.save(User.builder()
                    .email(role.name().toLowerCase() + "@inventario.com").passwordHash("x")
                    .fullName(role.name()).role(role).active(true).build()));
        } finally {
            TenantContext.clear();
        }
    }

    private UUID createItem(String name) throws Exception {
        String body = mockMvc.perform(post("/api/v1/inventory/items")
                        .cookie(cookieFor(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"unit\":\"kg\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(body.replaceAll(".*\"id\":\"([0-9a-fA-F-]+)\".*", "$1"));
    }

    private void register(UUID itemId, String json) throws Exception {
        mockMvc.perform(post("/api/v1/inventory/movements")
                        .cookie(cookieFor(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inventoryItemId\":\"" + itemId + "\"," + json))
                .andExpect(status().isCreated());
    }

    private void expectStock(UUID itemId, double expected) throws Exception {
        mockMvc.perform(get("/api/v1/inventory/items").cookie(cookieFor(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id=='" + itemId + "')].stock").value(expected));
    }

    @Test
    @DisplayName("una entrada suma al stock del insumo")
    void anEntryAddsToStock() throws Exception {
        UUID itemId = createItem("Cebolla");

        register(itemId, "\"movementType\":\"IN\",\"quantity\":20.000}");

        expectStock(itemId, 20.000);
    }

    @Test
    @DisplayName("una salida resta del stock")
    void anExitSubtractsFromStock() throws Exception {
        UUID itemId = createItem("Cebolla");
        register(itemId, "\"movementType\":\"IN\",\"quantity\":20.000}");

        register(itemId, "\"movementType\":\"OUT\",\"quantity\":5.000}");

        expectStock(itemId, 15.000);
    }

    @Test
    @DisplayName("una merma con motivo resta del stock")
    void aWasteWithReasonSubtractsFromStock() throws Exception {
        UUID itemId = createItem("Cebolla");
        register(itemId, "\"movementType\":\"IN\",\"quantity\":20.000}");

        register(itemId, "\"movementType\":\"WASTE\",\"quantity\":2.000,\"reason\":\"Se echo a perder\"}");

        expectStock(itemId, 18.000);
    }

    @Test
    @DisplayName("el stock puede quedar negativo y se reporta como NEGATIVE")
    void stockCanGoNegativeAndIsReported() throws Exception {
        UUID itemId = createItem("Cebolla");

        register(itemId, "\"movementType\":\"OUT\",\"quantity\":3.000}");

        mockMvc.perform(get("/api/v1/inventory/items").cookie(cookieFor(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].stock").value(-3.000))
                .andExpect(jsonPath("$[0].level").value("NEGATIVE"));
    }

    @Test
    @DisplayName("un ajuste guarda la diferencia con signo y el conteo en el motivo")
    void anAdjustmentStoresTheSignedDeltaAndKeepsTheCount() throws Exception {
        UUID itemId = createItem("Cebolla");
        register(itemId, "\"movementType\":\"IN\",\"quantity\":11.000}");

        mockMvc.perform(post("/api/v1/inventory/movements")
                        .cookie(cookieFor(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inventoryItemId\":\"" + itemId
                                + "\",\"movementType\":\"ADJUSTMENT\",\"countedStock\":8.000}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.quantity").value(-3.000))
                .andExpect(jsonPath("$.reason").value("Conteo fisico: 8"));

        expectStock(itemId, 8.000);
    }

    @Test
    @DisplayName("un ajuste concatena el motivo del usuario despues del conteo")
    void anAdjustmentAppendsTheUserReason() throws Exception {
        UUID itemId = createItem("Cebolla");
        register(itemId, "\"movementType\":\"IN\",\"quantity\":10.000}");

        mockMvc.perform(post("/api/v1/inventory/movements")
                        .cookie(cookieFor(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inventoryItemId\":\"" + itemId + "\",\"movementType\":\"ADJUSTMENT\","
                                + "\"countedStock\":12.000,\"reason\":\"Habia una caja sin registrar\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.quantity").value(2.000))
                .andExpect(jsonPath("$.reason")
                        .value("Conteo fisico: 12 - Habia una caja sin registrar"));
    }

    @Test
    @DisplayName("un ajuste cuyo conteo coincide con el stock da 409")
    void anAdjustmentThatChangesNothingIsRejected() throws Exception {
        UUID itemId = createItem("Cebolla");
        register(itemId, "\"movementType\":\"IN\",\"quantity\":10.000}");

        mockMvc.perform(post("/api/v1/inventory/movements")
                        .cookie(cookieFor(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inventoryItemId\":\"" + itemId
                                + "\",\"movementType\":\"ADJUSTMENT\",\"countedStock\":10.000}"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("mandar quantity junto con ADJUSTMENT da 400 y senala el campo")
    void quantityWithAnAdjustmentIsRejected() throws Exception {
        UUID itemId = createItem("Cebolla");

        mockMvc.perform(post("/api/v1/inventory/movements")
                        .cookie(cookieFor(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inventoryItemId\":\"" + itemId + "\",\"movementType\":\"ADJUSTMENT\","
                                + "\"countedStock\":8.000,\"quantity\":3.000}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.quantity").exists());
    }

    @Test
    @DisplayName("un ajuste sin countedStock da 400 y senala el campo")
    void anAdjustmentWithoutTheCountIsRejected() throws Exception {
        UUID itemId = createItem("Cebolla");

        mockMvc.perform(post("/api/v1/inventory/movements")
                        .cookie(cookieFor(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inventoryItemId\":\"" + itemId
                                + "\",\"movementType\":\"ADJUSTMENT\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.countedStock").exists());
    }

    @Test
    @DisplayName("mandar countedStock en una entrada da 400")
    void countedStockOnAnEntryIsRejected() throws Exception {
        UUID itemId = createItem("Cebolla");

        mockMvc.perform(post("/api/v1/inventory/movements")
                        .cookie(cookieFor(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inventoryItemId\":\"" + itemId
                                + "\",\"movementType\":\"IN\",\"countedStock\":8.000}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.countedStock").exists());
    }

    @Test
    @DisplayName("una merma sin motivo da 400: sin motivo no sirve para nada")
    void aWasteWithoutReasonIsRejected() throws Exception {
        UUID itemId = createItem("Cebolla");

        mockMvc.perform(post("/api/v1/inventory/movements")
                        .cookie(cookieFor(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inventoryItemId\":\"" + itemId
                                + "\",\"movementType\":\"WASTE\",\"quantity\":2.000}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.reason").exists());
    }

    @Test
    @DisplayName("cantidad cero o negativa en una entrada da 400")
    void nonPositiveQuantityIsRejected() throws Exception {
        UUID itemId = createItem("Cebolla");

        mockMvc.perform(post("/api/v1/inventory/movements")
                        .cookie(cookieFor(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inventoryItemId\":\"" + itemId
                                + "\",\"movementType\":\"IN\",\"quantity\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.quantity").exists());
    }

    @Test
    @DisplayName("un movimiento sobre un insumo dado de baja da 409")
    void aMovementOnAnInactiveItemIsRejected() throws Exception {
        UUID itemId = createItem("Cebolla");
        mockMvc.perform(patch("/api/v1/inventory/items/" + itemId)
                        .cookie(cookieFor(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/inventory/movements")
                        .cookie(cookieFor(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inventoryItemId\":\"" + itemId
                                + "\",\"movementType\":\"IN\",\"quantity\":5.000}"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("un movimiento sobre un insumo inexistente da 404")
    void aMovementOnANonexistentItemGives404() throws Exception {
        mockMvc.perform(post("/api/v1/inventory/movements")
                        .cookie(cookieFor(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inventoryItemId\":\"" + UUID.randomUUID()
                                + "\",\"movementType\":\"IN\",\"quantity\":5.000}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("KITCHEN puede registrar movimientos; WAITER no")
    void kitchenCanRegisterAndWaiterCannot() throws Exception {
        UUID itemId = createItem("Cebolla");

        mockMvc.perform(post("/api/v1/inventory/movements")
                        .cookie(cookieFor(persistedUser(UserRole.KITCHEN)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inventoryItemId\":\"" + itemId
                                + "\",\"movementType\":\"WASTE\",\"quantity\":1.000,\"reason\":\"Quemado\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/inventory/movements")
                        .cookie(cookieFor(persistedUser(UserRole.WAITER)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inventoryItemId\":\"" + itemId
                                + "\",\"movementType\":\"IN\",\"quantity\":1.000}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("el movimiento registra quien lo hizo y trae el nombre del insumo")
    void theListingCarriesTheItemNameAndTheAuthor() throws Exception {
        UUID itemId = createItem("Cebolla");
        register(itemId, "\"movementType\":\"IN\",\"quantity\":4.000}");

        mockMvc.perform(get("/api/v1/inventory/movements").cookie(cookieFor(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].itemName").value("Cebolla"))
                .andExpect(jsonPath("$[0].unit").value("kg"))
                .andExpect(jsonPath("$[0].movementType").value("IN"))
                .andExpect(jsonPath("$[0].userName").value("ADMIN"));
    }

    @Test
    @DisplayName("el listado manda el efecto con signo: una salida viaja negativa")
    void theListingCarriesTheSignedEffect() throws Exception {
        UUID itemId = createItem("Cebolla");
        register(itemId, "\"movementType\":\"IN\",\"quantity\":20.000}");
        register(itemId, "\"movementType\":\"OUT\",\"quantity\":5.000}");

        // quantity guarda lo que se capturo (5, sin signo) y signedQuantity el
        // efecto (-5). Sin el segundo, la interfaz pintaria "+5" en una salida.
        mockMvc.perform(get("/api/v1/inventory/movements")
                        .param("itemId", itemId.toString())
                        .cookie(cookieFor(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].movementType").value("OUT"))
                .andExpect(jsonPath("$[0].quantity").value(5.000))
                .andExpect(jsonPath("$[0].signedQuantity").value(-5.000))
                .andExpect(jsonPath("$[1].movementType").value("IN"))
                .andExpect(jsonPath("$[1].signedQuantity").value(20.000));
    }

    @Test
    @DisplayName("el listado filtra por itemId")
    void theListingFiltersByItem() throws Exception {
        UUID onionId = createItem("Cebolla");
        UUID tomatoId = createItem("Tomate");
        register(onionId, "\"movementType\":\"IN\",\"quantity\":4.000}");
        register(tomatoId, "\"movementType\":\"IN\",\"quantity\":7.000}");

        mockMvc.perform(get("/api/v1/inventory/movements")
                        .param("itemId", onionId.toString())
                        .cookie(cookieFor(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].itemName").value("Cebolla"));
    }

    @Test
    @DisplayName("un movimiento de hoy aparece en el dia local del negocio, no en el de UTC")
    void aMovementRegisteredTodayAppearsInTheBusinessDay() throws Exception {
        UUID itemId = createItem("Cebolla");
        register(itemId, "\"movementType\":\"IN\",\"quantity\":4.000}");

        // Sin parametros el rango es "hoy" en la zona del negocio. Si el rango
        // se resolviera en UTC, despues de las 18:00 locales este listado
        // saldria vacio -- justo a media cena.
        mockMvc.perform(get("/api/v1/inventory/movements").cookie(cookieFor(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @DisplayName("un rango que no incluye hoy sale vacio")
    void aRangeThatExcludesTodayIsEmpty() throws Exception {
        UUID itemId = createItem("Cebolla");
        register(itemId, "\"movementType\":\"IN\",\"quantity\":4.000}");

        mockMvc.perform(get("/api/v1/inventory/movements")
                        .param("from", "2020-01-01").param("to", "2020-01-31")
                        .cookie(cookieFor(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }
}
