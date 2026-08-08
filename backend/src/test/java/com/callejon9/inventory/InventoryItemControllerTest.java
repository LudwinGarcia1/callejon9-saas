package com.callejon9.inventory;

import com.callejon9.auth.service.JwtService;
import com.callejon9.platform.tenant.domain.Tenant;
import com.callejon9.platform.tenant.service.TenantOnboardingService;
import com.callejon9.tenancy.TenantContext;
import com.callejon9.user.domain.User;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Insumos")
class InventoryItemControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private TenantOnboardingService onboardingService;
    @Autowired private JwtService jwtService;
    @Autowired private JdbcTemplate jdbcTemplate;

    private Tenant tenant;
    private User admin;

    @BeforeEach
    void seed() {
        tenant = onboardingService.onboard("Insumos Test", "insumos-test",
                "admin@insumos.com", "Admin", "Secreto123!", "FREE");
        admin = fakeUser(UserRole.ADMIN);
    }

    @AfterEach
    void cleanUp() {
        TenantContext.clear();
        jdbcTemplate.update("DELETE FROM tenants WHERE slug = 'insumos-test'");
    }

    private Cookie cookieFor(User user) {
        return new Cookie("access_token", jwtService.generateAccessToken(user));
    }

    private User fakeUser(UserRole role) {
        User user = User.builder()
                .email(role.name().toLowerCase() + "@insumos.com").passwordHash("x")
                .fullName(role.name()).role(role).active(true).build();
        user.setId(UUID.randomUUID());
        user.setTenantId(tenant.getId());
        return user;
    }

    private UUID createItem(String name, String unit) throws Exception {
        String body = mockMvc.perform(post("/api/v1/inventory/items")
                        .cookie(cookieFor(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"unit\":\"" + unit + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(body.replaceAll(".*\"id\":\"([0-9a-fA-F-]+)\".*", "$1"));
    }

    /**
     * Nace con stock en cero, no en el minimo: el stock solo se mueve con un
     * movimiento. Como el minimo declarado es 5 y el stock es 0, el insumo nace
     * en LOW, y eso es exactamente la senal que se quiere -- aparece en la lista
     * de alertas hasta que alguien registre la entrada que lo surte.
     */
    @Test
    @DisplayName("ADMIN crea un insumo, nace con stock en cero y ya bajo su minimo")
    void adminCreatesAnItemThatStartsAtZero() throws Exception {
        mockMvc.perform(post("/api/v1/inventory/items")
                        .cookie(cookieFor(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Cebolla","unit":"kg","minStock":5.000,"unitCost":32.50}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Cebolla"))
                .andExpect(jsonPath("$.unit").value("kg"))
                .andExpect(jsonPath("$.stock").value(0))
                .andExpect(jsonPath("$.minStock").value(5.000))
                .andExpect(jsonPath("$.unitCost").value(32.50))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.level").value("LOW"));
    }

    @Test
    @DisplayName("un insumo sin minimo configurado nace en OK, no en alerta")
    void anItemWithoutAMinimumStartsOk() throws Exception {
        mockMvc.perform(post("/api/v1/inventory/items")
                        .cookie(cookieFor(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Sal\",\"unit\":\"kg\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.stock").value(0))
                .andExpect(jsonPath("$.level").value("OK"));
    }

    @Test
    @DisplayName("minStock y unitCost son opcionales y entran en cero")
    void minStockAndUnitCostDefaultToZero() throws Exception {
        mockMvc.perform(post("/api/v1/inventory/items")
                        .cookie(cookieFor(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Sal\",\"unit\":\"kg\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.minStock").value(0))
                .andExpect(jsonPath("$.unitCost").value(0));
    }

    @Test
    @DisplayName("crear un insumo con un nombre ya usado da 409")
    void duplicateNameIsRejected() throws Exception {
        createItem("Cebolla", "kg");

        mockMvc.perform(post("/api/v1/inventory/items")
                        .cookie(cookieFor(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Cebolla\",\"unit\":\"pieza\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("nombre vacio da 400")
    void blankNameIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/inventory/items")
                        .cookie(cookieFor(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"  \",\"unit\":\"kg\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").exists());
    }

    @Test
    @DisplayName("un WAITER puede consultar insumos pero no crearlos ni editarlos")
    void waiterCanReadButNotWrite() throws Exception {
        UUID itemId = createItem("Cebolla", "kg");
        User waiter = fakeUser(UserRole.WAITER);

        mockMvc.perform(get("/api/v1/inventory/items").cookie(cookieFor(waiter)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        mockMvc.perform(post("/api/v1/inventory/items")
                        .cookie(cookieFor(waiter))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Tomate\",\"unit\":\"kg\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/v1/inventory/items/" + itemId)
                        .cookie(cookieFor(waiter))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Cebolla morada\",\"unit\":\"kg\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/v1/inventory/items/" + itemId)
                        .cookie(cookieFor(waiter))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("ADMIN corrige nombre, minimo y costo, y el stock queda intacto")
    void adminUpdatesTheItemWithoutTouchingStock() throws Exception {
        UUID itemId = createItem("Cebolla", "kg");

        mockMvc.perform(put("/api/v1/inventory/items/" + itemId)
                        .cookie(cookieFor(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Cebolla morada","unit":"kg","minStock":8.000,"unitCost":40.00}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Cebolla morada"))
                .andExpect(jsonPath("$.minStock").value(8.000))
                .andExpect(jsonPath("$.unitCost").value(40.00))
                .andExpect(jsonPath("$.stock").value(0));
    }

    @Test
    @DisplayName("renombrar un insumo sobre el nombre de otro da 409")
    void renamingOntoAnExistingNameIsRejected() throws Exception {
        createItem("Cebolla", "kg");
        UUID tomatoId = createItem("Tomate", "kg");

        mockMvc.perform(put("/api/v1/inventory/items/" + tomatoId)
                        .cookie(cookieFor(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Cebolla\",\"unit\":\"kg\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("editar un insumo que no existe da 404")
    void updatingANonexistentItemGives404() throws Exception {
        mockMvc.perform(put("/api/v1/inventory/items/" + UUID.randomUUID())
                        .cookie(cookieFor(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Cebolla\",\"unit\":\"kg\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("PATCH da de baja un insumo y desaparece del listado por defecto")
    void patchDeactivatesAndHidesTheItem() throws Exception {
        UUID itemId = createItem("Cebolla", "kg");

        mockMvc.perform(patch("/api/v1/inventory/items/" + itemId)
                        .cookie(cookieFor(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        mockMvc.perform(get("/api/v1/inventory/items").cookie(cookieFor(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(get("/api/v1/inventory/items").param("includeInactive", "true")
                        .cookie(cookieFor(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].active").value(false));
    }

    @Test
    @DisplayName("reactivar un insumo lo hace reaparecer en el listado por defecto")
    void reactivatingMakesTheItemReappear() throws Exception {
        UUID itemId = createItem("Cebolla", "kg");

        mockMvc.perform(patch("/api/v1/inventory/items/" + itemId)
                        .cookie(cookieFor(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/v1/inventory/items/" + itemId)
                        .cookie(cookieFor(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));

        mockMvc.perform(get("/api/v1/inventory/items").cookie(cookieFor(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @DisplayName("el listado sale ordenado por nombre")
    void listIsOrderedByName() throws Exception {
        createItem("Tomate", "kg");
        createItem("Aceite", "litro");
        createItem("Cebolla", "kg");

        mockMvc.perform(get("/api/v1/inventory/items").cookie(cookieFor(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Aceite"))
                .andExpect(jsonPath("$[1].name").value("Cebolla"))
                .andExpect(jsonPath("$[2].name").value("Tomate"));
    }
}
