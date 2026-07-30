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
@DisplayName("Productos")
class ProductControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private TenantOnboardingService onboardingService;
    @Autowired private JwtService jwtService;
    @Autowired private JdbcTemplate jdbcTemplate;

    private Tenant tenant;
    private User admin;

    @BeforeEach
    void seed() {
        tenant = onboardingService.onboard("Productos Test", "productos-test",
                "admin@productos.com", "Admin", "Secreto123!", "FREE");
        User user = User.builder()
                .email("admin@productos.com").passwordHash("x")
                .fullName("Admin").role(UserRole.ADMIN).active(true).build();
        user.setId(UUID.randomUUID());
        user.setTenantId(tenant.getId());
        admin = user;
    }

    @AfterEach
    void cleanUp() {
        TenantContext.clear();
        jdbcTemplate.update("DELETE FROM tenants WHERE slug = 'productos-test'");
    }

    private Cookie cookieFor(User user) {
        return new Cookie("access_token", jwtService.generateAccessToken(user));
    }

    private UUID createCategory(String name) throws Exception {
        String body = mockMvc.perform(post("/api/v1/categories")
                        .cookie(cookieFor(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(body.replaceAll(".*\"id\":\"([0-9a-fA-F-]+)\".*", "$1"));
    }

    @Test
    @DisplayName("ADMIN puede crear un producto")
    void adminCanCreateAProduct() throws Exception {
        mockMvc.perform(post("/api/v1/products")
                        .cookie(cookieFor(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Taco","description":"Taco al pastor","price":25.50}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Taco"))
                .andExpect(jsonPath("$.price").value(25.50))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    @DisplayName("GET /products filtra por categoryId")
    void listFiltersByCategory() throws Exception {
        UUID bebidasId = createCategory("Bebidas");
        UUID postresId = createCategory("Postres");

        mockMvc.perform(post("/api/v1/products").cookie(cookieFor(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Agua\",\"price\":15.00,\"categoryId\":\"" + bebidasId + "\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/products").cookie(cookieFor(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Flan\",\"price\":40.00,\"categoryId\":\"" + postresId + "\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/products").param("categoryId", bebidasId.toString())
                        .cookie(cookieFor(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Agua"));
    }
}
