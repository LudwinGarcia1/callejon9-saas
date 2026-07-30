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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
}
