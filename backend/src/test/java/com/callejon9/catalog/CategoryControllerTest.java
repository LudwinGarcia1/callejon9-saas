package com.callejon9.catalog;

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
@DisplayName("Categorias")
class CategoryControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private TenantOnboardingService onboardingService;
    @Autowired private JwtService jwtService;
    @Autowired private JdbcTemplate jdbcTemplate;

    private Tenant tenant;
    private User admin;

    @BeforeEach
    void seed() {
        tenant = onboardingService.onboard("Categorias Test", "categorias-test",
                "admin@categorias.com", "Admin", "Secreto123!", "FREE");
        User user = User.builder()
                .email("admin@categorias.com").passwordHash("x")
                .fullName("Admin").role(UserRole.ADMIN).active(true).build();
        user.setId(UUID.randomUUID());
        user.setTenantId(tenant.getId());
        admin = user;
    }

    @AfterEach
    void cleanUp() {
        TenantContext.clear();
        jdbcTemplate.update("DELETE FROM tenants WHERE slug = 'categorias-test'");
    }

    private Cookie cookieFor(User user) {
        return new Cookie("access_token", jwtService.generateAccessToken(user));
    }

    @Test
    @DisplayName("ADMIN puede crear una categoria")
    void adminCanCreateACategory() throws Exception {
        mockMvc.perform(post("/api/v1/categories")
                        .cookie(cookieFor(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Bebidas","sortOrder":1}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Bebidas"));
    }

    @Test
    @DisplayName("un nombre de categoria duplicado da 409")
    void duplicateCategoryNameIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/categories")
                        .cookie(cookieFor(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Postres"}
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/categories")
                        .cookie(cookieFor(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Postres"}
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("GET /categories ordena por sortOrder y luego por nombre")
    void listOrdersBySortOrderThenName() throws Exception {
        mockMvc.perform(post("/api/v1/categories").cookie(cookieFor(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Zeta\",\"sortOrder\":1}"))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/categories").cookie(cookieFor(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Alfa\",\"sortOrder\":1}"))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/categories").cookie(cookieFor(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Primero\",\"sortOrder\":0}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/categories").cookie(cookieFor(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Primero"))
                .andExpect(jsonPath("$[1].name").value("Alfa"))
                .andExpect(jsonPath("$[2].name").value("Zeta"));
    }
}
