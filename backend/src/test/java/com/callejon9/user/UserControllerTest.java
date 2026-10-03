package com.callejon9.user;

import com.callejon9.platform.tenant.domain.Tenant;
import com.callejon9.platform.tenant.service.TenantOnboardingService;
import com.callejon9.shared.error.BusinessRuleException;
import com.callejon9.support.TestSessions;
import com.callejon9.tenancy.TenantContext;
import com.callejon9.user.domain.User;
import com.callejon9.user.repository.UserRepository;
import com.callejon9.user.service.UserService;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
    @Autowired private TestSessions testSessions;
    @Autowired private UserRepository userRepository;
    @Autowired private UserService userService;
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
        return new Cookie("access_token", testSessions.accessTokenFor(user));
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

        // Ahora "admin" es el unico administrador activo. Desde CAL-95 un
        // administrador desactivado ya no conserva sesion, asi que por HTTP
        // nadie mas puede intentar darlo de baja; la regla se ejerce en el
        // servicio, que es donde vive (y la carrera real la cubre
        // UserServiceConcurrencyTest).
        assertThatThrownBy(() -> deactivateAsService(admin.getId(), secondAdmin.getId()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("ultimo administrador");
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

    // --- CAL-95: desactivar revoca las sesiones -----------------------------

    @Test
    @DisplayName("desactivar a un usuario revoca al instante todas sus sesiones")
    void deactivatingAUserRevokesAllTheirSessions() throws Exception {
        UUID waiterId = createUserAs(admin, "mesero5@usuarios.com", "WAITER", "Mesero Cinco");
        Session phone = login("usuarios-test", "mesero5@usuarios.com");
        Session tablet = login("usuarios-test", "mesero5@usuarios.com");
        expectMe(phone.access(), 200);

        deactivate(waiterId, cookieFor(admin)).andExpect(status().isOk());

        expectMe(phone.access(), 401);
        expectMe(tablet.access(), 401);
        mockMvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie("refresh_token", phone.refresh())))
                .andExpect(status().isUnauthorized());

        // Reactivar no resucita las sesiones revocadas: hay que volver a entrar.
        mockMvc.perform(patch("/api/v1/users/" + waiterId)
                        .cookie(cookieFor(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":true}"))
                .andExpect(status().isOk());
        expectMe(phone.access(), 401);
        expectMe(login("usuarios-test", "mesero5@usuarios.com").access(), 200);
    }

    @Test
    @DisplayName("desactivar a un usuario no toca las sesiones de los demas")
    void deactivationLeavesOtherUsersSessionsAlone() throws Exception {
        UUID waiterId = createUserAs(admin, "mesero6@usuarios.com", "WAITER", "Mesero Seis");
        createUserAs(admin, "mesero7@usuarios.com", "WAITER", "Mesero Siete");
        Session otherWaiter = login("usuarios-test", "mesero7@usuarios.com");
        Cookie adminCookie = cookieFor(admin);

        deactivate(waiterId, adminCookie).andExpect(status().isOk());

        expectMe(otherWaiter.access(), 200);
        mockMvc.perform(get("/api/v1/users").cookie(adminCookie))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("una desactivacion rechazada por regla de negocio no revoca ninguna sesion")
    void rejectedDeactivationRevokesNothing() throws Exception {
        Cookie adminCookie = cookieFor(admin);

        // Autodesactivacion rechazada: la sesion del administrador sigue viva.
        deactivate(admin.getId(), adminCookie).andExpect(status().isConflict());
        expectMe(adminCookie.getValue(), 200);

        // Ultimo administrador activo: la regla rechaza la baja (se ejerce en el
        // servicio, ver lastActiveAdminCannotBeDeactivatedButASecondAdminCan)
        // y la transaccion se deshace sin revocar la sesion del administrador.
        UUID secondAdminId = createUserAs(admin, "admin3@usuarios.com", "ADMIN", "Admin Tres");
        deactivate(secondAdminId, adminCookie).andExpect(status().isOk());

        assertThatThrownBy(() -> deactivateAsService(admin.getId(), secondAdminId))
                .isInstanceOf(BusinessRuleException.class);
        expectMe(adminCookie.getValue(), 200);
    }

    @Test
    @DisplayName("intentar desactivar a un usuario de otro restaurante no revoca sus sesiones")
    void deactivationNeverReachesAnotherTenantsSessions() throws Exception {
        Tenant tenantB = onboardingService.onboard("Restaurante B", "usuarios-test-b",
                "admin@b-usuarios.com", "Admin B", "Secreto123!", "FREE");
        User adminB = persistedUser(tenantB.getId(), "admin@b-usuarios.com");
        UUID waiterBId = createUserAs(adminB, "meserob2@usuarios.com", "WAITER", "Mesero B2");
        Session waiterB = login("usuarios-test-b", "meserob2@usuarios.com");

        deactivate(waiterBId, cookieFor(admin)).andExpect(status().isNotFound());

        expectMe(waiterB.access(), 200);
    }

    private record Session(String access, String refresh) {
    }

    private Session login(String slug, String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"slug":"%s","email":"%s","password":"Secreto123!"}
                                """.formatted(slug, email)))
                .andExpect(status().isOk())
                .andReturn();
        return new Session(
                result.getResponse().getCookie("access_token").getValue(),
                result.getResponse().getCookie("refresh_token").getValue());
    }

    private ResultActions deactivate(UUID userId, Cookie caller) throws Exception {
        return mockMvc.perform(patch("/api/v1/users/" + userId)
                .cookie(caller)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"active\":false}"));
    }

    private User deactivateAsService(UUID targetId, UUID callerId) {
        return TenantContext.callAs(tenant.getId(),
                () -> userService.setActive(targetId, false, callerId));
    }

    private void expectMe(String accessToken, int expectedStatus) throws Exception {
        mockMvc.perform(get("/api/v1/auth/me").cookie(new Cookie("access_token", accessToken)))
                .andExpect(status().is(expectedStatus));
    }
}
