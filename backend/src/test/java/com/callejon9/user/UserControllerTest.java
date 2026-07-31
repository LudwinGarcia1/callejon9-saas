package com.callejon9.user;

import com.callejon9.auth.service.JwtService;
import com.callejon9.platform.tenant.domain.Tenant;
import com.callejon9.platform.tenant.service.TenantOnboardingService;
import com.callejon9.tenancy.TenantContext;
import com.callejon9.user.domain.User;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cubre las reglas de la Task 8 (gestion de usuarios): creacion con limite de
 * plan, unicidad de correo por tenant, rechazo de SUPER_ADMIN, desactivacion
 * logica con sus dos protecciones (auto-desactivacion y ultimo administrador)
 * y aislamiento multi-tenant en esta capa.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Gestion de usuarios del restaurante")
class UserControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private TenantOnboardingService onboardingService;
    @Autowired private JwtService jwtService;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private TransactionTemplate transactionTemplate;

    private Tenant tenant;
    private User admin;

    @BeforeEach
    void seed() {
        tenant = onboardingService.onboard("Usuarios Test", "usuarios-test",
                "admin@usuarios.com", "Admin", "Secreto123!", "FREE");
        admin = persistedUser(tenant.getId(), "admin@usuarios.com");
    }

    @AfterEach
    void cleanUp() {
        TenantContext.clear();
        jdbcTemplate.update("DELETE FROM tenants WHERE slug LIKE 'usuarios-test%'");
    }

    private User persistedUser(UUID tenantId, String email) {
        TenantContext.set(tenantId);
        try {
            return transactionTemplate.execute(
                    status -> userRepository.findByEmail(email).orElseThrow());
        } finally {
            TenantContext.clear();
        }
    }

    private Cookie cookieFor(User user) {
        return new Cookie("access_token", jwtService.generateAccessToken(user));
    }

    private UUID createUserAs(User caller, String email, String role, String fullName) throws Exception {
        String body = mockMvc.perform(post("/api/v1/users")
                        .cookie(cookieFor(caller))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"fullName\":\"" + fullName
                                + "\",\"role\":\"" + role + "\",\"password\":\"Secreto123!\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(body.replaceAll(".*\"id\":\"([0-9a-fA-F-]+)\".*", "$1"));
    }

    @Test
    @DisplayName("ADMIN puede crear un usuario y la contrasena queda hasheada")
    void creationSucceedsAndPasswordVerifiesThroughEncoder() throws Exception {
        mockMvc.perform(post("/api/v1/users")
                        .cookie(cookieFor(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"mesero@usuarios.com","fullName":"Mesero Uno","role":"WAITER","password":"Secreto123!"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("mesero@usuarios.com"))
                .andExpect(jsonPath("$.role").value("WAITER"))
                .andExpect(jsonPath("$.active").value(true));

        User saved = persistedUser(tenant.getId(), "mesero@usuarios.com");
        assertThat(passwordEncoder.matches("Secreto123!", saved.getPasswordHash())).isTrue();
    }

    @Test
    @DisplayName("un correo duplicado en el mismo restaurante da 409, pero en otro restaurante funciona")
    void duplicateEmailInSameTenantConflictsButAnotherTenantSucceeds() throws Exception {
        mockMvc.perform(post("/api/v1/users")
                        .cookie(cookieFor(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"repetido@usuarios.com","fullName":"Uno","role":"WAITER","password":"Secreto123!"}
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/users")
                        .cookie(cookieFor(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"repetido@usuarios.com","fullName":"Dos","role":"WAITER","password":"Secreto123!"}
                                """))
                .andExpect(status().isConflict());

        Tenant otherTenant = onboardingService.onboard("Otro Restaurante", "usuarios-test-otro",
                "admin@otro-usuarios.com", "Admin Otro", "Secreto123!", "FREE");
        User otherAdmin = persistedUser(otherTenant.getId(), "admin@otro-usuarios.com");

        mockMvc.perform(post("/api/v1/users")
                        .cookie(cookieFor(otherAdmin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"repetido@usuarios.com","fullName":"Tres","role":"WAITER","password":"Secreto123!"}
                                """))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("SUPER_ADMIN no puede crearse desde un restaurante")
    void superAdminRoleIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/users")
                        .cookie(cookieFor(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"superadmin@usuarios.com","fullName":"Super","role":"SUPER_ADMIN","password":"Secreto123!"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("el plan FREE topa en el cuarto usuario")
    void planCeilingTriggersOnTheFourthUser() throws Exception {
        // El onboarding ya creo 1 admin; el plan FREE permite 3.
        createUserAs(admin, "u2@usuarios.com", "WAITER", "U2");
        createUserAs(admin, "u3@usuarios.com", "WAITER", "U3");

        mockMvc.perform(post("/api/v1/users")
                        .cookie(cookieFor(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"u4@usuarios.com","fullName":"U4","role":"WAITER","password":"Secreto123!"}
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("GET /users lista los usuarios del restaurante sin exponer datos sensibles")
    void listingReturnsUsersWithoutSensitiveFields() throws Exception {
        createUserAs(admin, "mesero2@usuarios.com", "WAITER", "Mesero Dos");

        mockMvc.perform(get("/api/v1/users").cookie(cookieFor(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].email").value("admin@usuarios.com"))
                .andExpect(jsonPath("$[1].email").value("mesero2@usuarios.com"))
                .andExpect(jsonPath("$[1].role").value("WAITER"))
                .andExpect(jsonPath("$[1].active").value(true))
                .andExpect(jsonPath("$[0].passwordHash").doesNotExist())
                .andExpect(jsonPath("$[0].totpSecret").doesNotExist());
    }

    @Test
    @DisplayName("un administrador no puede desactivarse a si mismo")
    void selfDeactivationIsRefused() throws Exception {
        mockMvc.perform(patch("/api/v1/users/" + admin.getId())
                        .cookie(cookieFor(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("el ultimo administrador activo no puede desactivarse, pero un segundo administrador si")
    void lastActiveAdminCannotBeDeactivatedButASecondAdminCan() throws Exception {
        UUID secondAdminId = createUserAs(admin, "admin2@usuarios.com", "ADMIN", "Admin Dos");
        User secondAdmin = persistedUser(tenant.getId(), "admin2@usuarios.com");

        // Con dos administradores activos, desactivar a uno de ellos es valido.
        mockMvc.perform(patch("/api/v1/users/" + secondAdminId)
                        .cookie(cookieFor(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        // Ahora "admin" es el unico administrador activo: ni siquiera otro
        // administrador (el token de secondAdmin sigue siendo valido) puede
        // desactivarlo.
        mockMvc.perform(patch("/api/v1/users/" + admin.getId())
                        .cookie(cookieFor(secondAdmin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("un usuario desactivado ya no puede iniciar sesion")
    void aDeactivatedUserCanNoLongerLogIn() throws Exception {
        UUID waiterId = createUserAs(admin, "mesero3@usuarios.com", "WAITER", "Mesero Tres");

        mockMvc.perform(patch("/api/v1/users/" + waiterId)
                        .cookie(cookieFor(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"slug":"usuarios-test","email":"mesero3@usuarios.com","password":"Secreto123!"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("un rol distinto de ADMIN recibe 403 en los tres endpoints")
    void nonAdminRoleReceives403OnAllThreeEndpoints() throws Exception {
        createUserAs(admin, "mesero4@usuarios.com", "WAITER", "Mesero Cuatro");
        User waiter = persistedUser(tenant.getId(), "mesero4@usuarios.com");

        mockMvc.perform(post("/api/v1/users")
                        .cookie(cookieFor(waiter))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"otro@usuarios.com","fullName":"Otro","role":"WAITER","password":"Secreto123!"}
                                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/users").cookie(cookieFor(waiter)))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/v1/users/" + admin.getId())
                        .cookie(cookieFor(waiter))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("un administrador del restaurante A nunca ve ni modifica usuarios del restaurante B")
    void crossTenantIsolationIsEnforced() throws Exception {
        Tenant tenantB = onboardingService.onboard("Restaurante B", "usuarios-test-b",
                "admin@b-usuarios.com", "Admin B", "Secreto123!", "FREE");
        User adminB = persistedUser(tenantB.getId(), "admin@b-usuarios.com");
        UUID waiterBId = createUserAs(adminB, "meserob@usuarios.com", "WAITER", "Mesero B");

        // El admin de A no ve al usuario de B en su listado (solo se ve a si mismo).
        mockMvc.perform(get("/api/v1/users").cookie(cookieFor(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].email").value("admin@usuarios.com"));

        // El admin de A no puede modificar al usuario de B: RLS lo oculta, 404.
        mockMvc.perform(patch("/api/v1/users/" + waiterBId)
                        .cookie(cookieFor(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isNotFound());
    }
}
