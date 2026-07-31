package com.callejon9.table;

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
@DisplayName("Mesas")
class TableControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private TenantOnboardingService onboardingService;
    @Autowired private JwtService jwtService;
    @Autowired private JdbcTemplate jdbcTemplate;

    private Tenant tenant;
    private User admin;

    @BeforeEach
    void seed() {
        tenant = onboardingService.onboard("Mesas Test", "mesas-test",
                "admin@mesas.com", "Admin", "Secreto123!", "FREE");
        admin = fakeUser(tenant.getId(), UserRole.ADMIN);
    }

    @AfterEach
    void cleanUp() {
        TenantContext.clear();
        jdbcTemplate.update("DELETE FROM tenants WHERE slug = 'mesas-test'");
    }

    private User fakeUser(UUID tenantId, UserRole role) {
        User user = User.builder()
                .email(role.name().toLowerCase() + "@mesas.com").passwordHash("x")
                .fullName(role.name()).role(role).active(true).build();
        user.setId(UUID.randomUUID());
        user.setTenantId(tenantId);
        return user;
    }

    private Cookie cookieFor(User user) {
        return new Cookie("access_token", jwtService.generateAccessToken(user));
    }

    private UUID createTable(int number, int capacity) throws Exception {
        String body = mockMvc.perform(post("/api/v1/tables")
                        .cookie(cookieFor(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"number\":" + number + ",\"capacity\":" + capacity + "}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(body.replaceAll(".*\"id\":\"([0-9a-fA-F-]+)\".*", "$1"));
    }

    @Test
    @DisplayName("ADMIN puede crear una mesa")
    void adminCanCreateATable() throws Exception {
        mockMvc.perform(post("/api/v1/tables")
                        .cookie(cookieFor(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"number":1,"capacity":4}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.number").value(1))
                .andExpect(jsonPath("$.status").value("FREE"));
    }

    @Test
    @DisplayName("un numero de mesa duplicado da 409")
    void duplicateTableNumberIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/tables")
                        .cookie(cookieFor(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"number":5,"capacity":2}
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/tables")
                        .cookie(cookieFor(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"number":5,"capacity":6}
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("WAITER no puede crear mesas")
    void waiterCannotCreateTables() throws Exception {
        User waiter = fakeUser(tenant.getId(), UserRole.WAITER);

        mockMvc.perform(post("/api/v1/tables")
                        .cookie(cookieFor(waiter))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"number":9,"capacity":4}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /tables lista las mesas activas con su estado")
    void listReturnsActiveTables() throws Exception {
        mockMvc.perform(post("/api/v1/tables")
                        .cookie(cookieFor(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"number":3,"capacity":4}
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/tables").cookie(cookieFor(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].number").value(3))
                .andExpect(jsonPath("$[0].status").value("FREE"));
    }

    @Test
    @DisplayName("ADMIN puede renumerar una mesa y cambiar su capacidad")
    void adminCanUpdateATable() throws Exception {
        UUID tableId = createTable(7, 4);

        mockMvc.perform(put("/api/v1/tables/" + tableId)
                        .cookie(cookieFor(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"number\":8,\"capacity\":6}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.number").value(8))
                .andExpect(jsonPath("$.capacity").value(6));
    }

    @Test
    @DisplayName("renumerar una mesa a un numero ya usado por otra mesa da 409")
    void renumberingATableOntoAnExistingNumberIsRejected() throws Exception {
        createTable(1, 4);
        UUID secondTableId = createTable(2, 4);

        mockMvc.perform(put("/api/v1/tables/" + secondTableId)
                        .cookie(cookieFor(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"number\":1,\"capacity\":4}"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("editar una mesa que no existe da 404")
    void updatingANonexistentTableGives404() throws Exception {
        mockMvc.perform(put("/api/v1/tables/" + UUID.randomUUID())
                        .cookie(cookieFor(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"number\":1,\"capacity\":4}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("PATCH da de baja una mesa y esta deja de aparecer en el listado")
    void patchDeactivatesATableAndItDisappearsFromTheListing() throws Exception {
        UUID tableId = createTable(9, 4);

        mockMvc.perform(patch("/api/v1/tables/" + tableId)
                        .cookie(cookieFor(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        mockMvc.perform(get("/api/v1/tables").cookie(cookieFor(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("GET /tables?includeInactive=true incluye mesas dadas de baja")
    void listWithIncludeInactiveIncludesInactiveTables() throws Exception {
        UUID tableId = createTable(11, 4);

        mockMvc.perform(patch("/api/v1/tables/" + tableId)
                        .cookie(cookieFor(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/tables").cookie(cookieFor(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(get("/api/v1/tables").param("includeInactive", "true")
                        .cookie(cookieFor(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].number").value(11))
                .andExpect(jsonPath("$[0].active").value(false));
    }

    @Test
    @DisplayName("reactivar una mesa la hace reaparecer en el listado por defecto")
    void reactivatingATableMakesItReappearInTheDefaultListing() throws Exception {
        UUID tableId = createTable(12, 4);

        mockMvc.perform(patch("/api/v1/tables/" + tableId)
                        .cookie(cookieFor(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/v1/tables/" + tableId)
                        .cookie(cookieFor(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));

        mockMvc.perform(get("/api/v1/tables").cookie(cookieFor(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].number").value(12));
    }

    @Test
    @DisplayName("un WAITER no puede editar ni dar de baja mesas")
    void waiterCannotUpdateOrDeactivateTables() throws Exception {
        UUID tableId = createTable(4, 4);
        User waiter = fakeUser(tenant.getId(), UserRole.WAITER);

        mockMvc.perform(put("/api/v1/tables/" + tableId)
                        .cookie(cookieFor(waiter))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"number\":10,\"capacity\":4}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/v1/tables/" + tableId)
                        .cookie(cookieFor(waiter))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isForbidden());
    }
}
