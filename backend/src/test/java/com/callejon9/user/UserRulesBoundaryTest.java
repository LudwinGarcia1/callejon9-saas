package com.callejon9.user;

import com.callejon9.platform.tenant.domain.Tenant;
import com.callejon9.platform.tenant.service.TenantOnboardingService;
import com.callejon9.support.TestSessions;
import com.callejon9.tenancy.TenantContext;
import com.callejon9.user.domain.User;
import com.callejon9.user.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Reglas del alta de usuarios en sus bordes (CAL-122, alcance ajustado): el
 * rol de la sesion, la contrasena al crear, el cupo del plan con usuarios
 * desactivados y las validaciones del correo y el nombre. Complementa a
 * {@link UserControllerTest}, que cubre el camino feliz, la desactivacion y el
 * aislamiento entre restaurantes.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("UT · reglas de usuarios y roles en el borde")
class UserRulesBoundaryTest {

    private static final String SLUG = "usuarios-borde";
    private static final String PASSWORD = "Mantel-Azul-47";

    @Autowired private MockMvc mockMvc;
    @Autowired private TenantOnboardingService onboardingService;
    @Autowired private TestSessions testSessions;
    @Autowired private UserRepository userRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private TransactionTemplate transactionTemplate;

    private Cookie adminCookie;

    @BeforeEach
    void seed() {
        Tenant tenant = onboardingService.onboard("Usuarios Borde", SLUG,
                "admin@usuarios-borde.com", "Admin Borde", PASSWORD, "FREE");
        User admin = TenantContext.callAs(tenant.getId(), () -> transactionTemplate.execute(
                status -> userRepository.findByEmail("admin@usuarios-borde.com").orElseThrow()));
        adminCookie = new Cookie("access_token", testSessions.accessTokenFor(admin));
    }

    @AfterEach
    void cleanUp() {
        TenantContext.clear();
        jdbcTemplate.update("DELETE FROM tenants WHERE slug = ?", SLUG);
    }

    private ResultActions createUser(String email, String fullName, String role, String password) throws Exception {
        String roleJson = role == null ? "null" : "\"" + role + "\"";
        return mockMvc.perform(post("/api/v1/users")
                .cookie(adminCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"fullName\":\"" + fullName + "\",\"role\":" + roleJson
                        + ",\"password\":\"" + password + "\"}"));
    }

    private UUID createdId(ResultActions result) throws Exception {
        String body = result.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return UUID.fromString(body.replaceAll(".*\"id\":\"([0-9a-fA-F-]+)\".*", "$1"));
    }

    private ResultActions setActive(UUID userId, boolean active) throws Exception {
        return mockMvc.perform(patch("/api/v1/users/" + userId)
                .cookie(adminCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"active\":" + active + "}"));
    }

    private ResultActions login(String email) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"slug\":\"" + SLUG + "\",\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"));
    }

    private Cookie accessCookieOf(MvcResult loginResult) {
        return new Cookie("access_token", loginResult.getResponse().getCookie("access_token").getValue());
    }

    @Nested
    @DisplayName("El rol y la sesión")
    class RoleAndSession {

        @ParameterizedTest(name = "un usuario {0} inicia sesión con el rol {0}")
        @ValueSource(strings = {"ADMIN", "WAITER", "KITCHEN", "CASHIER"})
        @DisplayName("Válido: el rol con que se crea el usuario es el de su sesión en login y en /auth/me")
        void sessionCarriesTheRoleAssignedAtCreation(String role) throws Exception {
            String email = "nuevo-" + role.toLowerCase() + "@usuarios-borde.com";
            createdId(createUser(email, "Usuario " + role, role, PASSWORD));

            MvcResult session = login(email)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.role").value(role))
                    .andReturn();

            mockMvc.perform(get("/api/v1/auth/me").cookie(accessCookieOf(session)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.role").value(role));
        }

        @Test
        @DisplayName("Borde: tras desactivar y reactivar, la sesión vieja sigue revocada y la nueva conserva el rol")
        void reactivatedUserNeedsANewSessionWithTheSameRole() throws Exception {
            UUID cashierId = createdId(createUser("caja@usuarios-borde.com", "Caja Uno", "CASHIER", PASSWORD));
            Cookie oldSession = accessCookieOf(login("caja@usuarios-borde.com").andReturn());

            setActive(cashierId, false).andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));
            setActive(cashierId, true).andExpect(status().isOk()).andExpect(jsonPath("$.active").value(true));

            mockMvc.perform(get("/api/v1/auth/me").cookie(oldSession)).andExpect(status().isUnauthorized());
            MvcResult newSession = login("caja@usuarios-borde.com")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.role").value("CASHIER"))
                    .andReturn();
            mockMvc.perform(get("/api/v1/auth/me").cookie(accessCookieOf(newSession)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.role").value("CASHIER"));
        }

        @ParameterizedTest(name = "rol {0} se rechaza con 400")
        @ValueSource(strings = {"SUPER_ADMIN", "GERENTE", "waiter", ""})
        @DisplayName("Inválido: SUPER_ADMIN, un rol inexistente, en minúsculas o vacío se rechazan sin crear al usuario")
        void invalidRolesAreRejected(String role) throws Exception {
            createUser("rol-invalido@usuarios-borde.com", "Rol Invalido", role, PASSWORD)
                    .andExpect(status().isBadRequest());

            login("rol-invalido@usuarios-borde.com").andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Inválido: sin rol se rechaza con 400 en errors.role")
        void missingRoleIsRejected() throws Exception {
            createUser("sin-rol@usuarios-borde.com", "Sin Rol", null, PASSWORD)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.role").exists());
        }
    }

    @Nested
    @DisplayName("La contraseña al crear al usuario")
    class PasswordAtCreation {

        @Test
        @DisplayName("Borde: 10 caracteres es el mínimo aceptado")
        void tenCharactersAreAccepted() throws Exception {
            createdId(createUser("diez@usuarios-borde.com", "Mesero Borde", "WAITER", "Azul-47890"));
        }

        @ParameterizedTest(name = "«{0}» → {1}")
        @CsvSource(delimiter = '|', value = {
                "Azul-4789        | La contrasena debe tener al menos 10 caracteres.",
                "Mantel-Azul-Rojo | La contrasena debe combinar letras y numeros.",
                "aaaaaaaaa1       | La contrasena repite demasiado los mismos caracteres.",
                "qwerty123456     | Esa contrasena es demasiado comun. Elige otra."})
        @DisplayName("Inválido: corta (9), sin números, repetitiva o común, con el mensaje en errors.password")
        void weakPasswordsAreRejected(String password, String message) throws Exception {
            createUser("debil@usuarios-borde.com", "Mesero Borde", "WAITER", password)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.password").value(message));
        }

        @ParameterizedTest(name = "«{0}» contiene un dato personal")
        @ValueSource(strings = {"Delgado-2026x", "rmontes-2026", "ROSA-azul-47"})
        @DisplayName("Inválido: con el apellido, la parte local del correo o el nombre, sin importar mayúsculas")
        void passwordsWithPersonalDataAreRejected(String password) throws Exception {
            createUser("rmontes@usuarios-borde.com", "Rosa Delgado", "WAITER", password)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.password").value(
                            "La contrasena no debe incluir tu nombre, tu correo ni el identificador del restaurante."));
        }
    }

    @Nested
    @DisplayName("El cupo del plan con usuarios desactivados")
    class PlanSlotsWithInactiveUsers {

        @Test
        @DisplayName("Borde: un usuario desactivado sigue ocupando cupo y reactivarlo no rebasa el tope")
        void inactiveUsersKeepTheirSlot() throws Exception {
            // FREE permite 3: el administrador del alta y dos usuarios mas.
            createdId(createUser("u2@usuarios-borde.com", "Usuario Dos", "WAITER", PASSWORD));
            UUID third = createdId(createUser("u3@usuarios-borde.com", "Usuario Tres", "KITCHEN", PASSWORD));

            setActive(third, false).andExpect(status().isOk());
            createUser("u4@usuarios-borde.com", "Usuario Cuatro", "WAITER", PASSWORD)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.detail").value("Alcanzaste el limite de 3 usuarios del plan FREE."));

            setActive(third, true).andExpect(status().isOk());
            login("u3@usuarios-borde.com").andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("Las validaciones del alta")
    class CreationValidation {

        /** Un correo valido de exactamente {@code length} caracteres; las etiquetas del dominio no pasan de 63. */
        private String emailOfLength(int length) {
            String fixed = "m@" + "a".repeat(63) + "." + "b".repeat(63) + ".";
            return fixed + "c".repeat(length - fixed.length() - 4) + ".com";
        }

        @Test
        @DisplayName("Borde: correo de 180 caracteres y nombre de 160 se aceptan")
        void maximumLengthsAreAccepted() throws Exception {
            createdId(createUser(emailOfLength(180), "Largo", "WAITER", PASSWORD));
            createdId(createUser("nombre-largo@usuarios-borde.com", "N".repeat(160), "WAITER", PASSWORD));
        }

        @Test
        @DisplayName("Inválido: correo de 181, nombre de 161, correo sin formato o nombre vacío dan 400 en su campo")
        void invalidFieldsAreRejected() throws Exception {
            createUser(emailOfLength(181), "Largo", "WAITER", PASSWORD)
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.email").exists());
            createUser("nombre-largo@usuarios-borde.com", "N".repeat(161), "WAITER", PASSWORD)
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.fullName").exists());
            createUser("no-es-correo", "Sin Formato", "WAITER", PASSWORD)
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.email").exists());
            createUser("vacio@usuarios-borde.com", " ", "WAITER", PASSWORD)
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.fullName").exists());
        }

        @Disabled("CAL-180: el correo distingue mayúsculas; se activa con la corrección")
        @Test
        @DisplayName("Inválido: un correo que solo cambia en mayúsculas es el mismo usuario y da 409")
        void emailDifferingOnlyInCaseIsADuplicate() throws Exception {
            createdId(createUser("mesero@usuarios-borde.com", "Mesero Uno", "WAITER", PASSWORD));

            createUser("Mesero@Usuarios-Borde.com", "Mesero Dos", "WAITER", PASSWORD)
                    .andExpect(status().isConflict());
        }

        @Disabled("CAL-180: el correo distingue mayúsculas; se activa con la corrección")
        @Test
        @DisplayName("Válido: un usuario creado con mayúsculas en el correo entra con el correo en minúsculas, como lo envía la app móvil")
        void userCreatedWithUppercaseEmailLogsInWithLowercase() throws Exception {
            createdId(createUser("Ana.Caja@Usuarios-Borde.com", "Ana Caja", "CASHIER", PASSWORD));

            login("ana.caja@usuarios-borde.com").andExpect(status().isOk());
        }
    }
}
