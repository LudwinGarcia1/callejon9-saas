package com.callejon9.auth;

import com.callejon9.platform.tenant.service.TenantOnboardingService;
import com.callejon9.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Freno contra fuerza bruta en el login. El limitador es un bean compartido
 * por todo el contexto de pruebas, asi que cada prueba usa su propia IP de
 * origen y su propia cuenta para no heredar fallos de otras clases. Las
 * reglas de la ventana se prueban con reloj controlado en
 * {@code LoginAttemptLimiterTest}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Limite de intentos de login")
class LoginRateLimitTest {

    private static final String PASSWORD = "Mantel-Azul-47";

    /**
     * Cadena real de Azure (CAL-169): el contenedor recibe todo desde una
     * direccion de enlace local de App Service, y lo que entra por la web
     * trae al final la IP de salida del frontend (192.0.2.10 en
     * application-test.yml). Antes del arreglo todas compartian una llave.
     */
    private static final String APP_SERVICE_HOP = "169.254.129.3";
    private static final String FRONTEND_OUTBOUND = "192.0.2.10";
    /** app.client-ip.proxy-secret de application-test.yml. */
    private static final String PROXY_SECRET = "secreto-de-prueba-del-proxy-de-next-32b";

    @Autowired private MockMvc mockMvc;
    @Autowired private TenantOnboardingService onboardingService;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Value("${app.auth.login-limit.max-failures-per-account}") private int maxPerAccount;
    @Value("${app.auth.login-limit.max-failures-per-ip}") private int maxPerIp;

    /** Un restaurante por prueba: un bloqueo no debe pasar de una prueba a otra. */
    private String slug;

    @BeforeEach
    void seed(TestInfo testInfo) {
        slug = "limite-" + testInfo.getTestMethod().orElseThrow().getName()
                .replaceAll("([A-Z])", "-$1").toLowerCase();
        onboardingService.onboard("Limite Test", slug, "admin@limite.mx", "Admin", PASSWORD, "FREE");
    }

    @AfterEach
    void cleanUp() {
        TenantContext.clear();
        jdbcTemplate.update("DELETE FROM tenants WHERE slug = ?", slug);
    }

    private ResultActions login(String remoteAddress, String email, String password) throws Exception {
        return login(remoteAddress, null, email, password);
    }

    private ResultActions login(String remoteAddress, String forwardedFor,
                                String email, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                .with(request -> {
                    request.setRemoteAddr(remoteAddress);
                    if (forwardedFor != null) {
                        request.addHeader("X-Forwarded-For", forwardedFor);
                    }
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"slug":"%s","email":"%s","password":"%s"}
                        """.formatted(slug, email, password)));
    }

    @Test
    @DisplayName("P09: tras 5 fallos la cuenta se bloquea, incluso con la contrasena correcta")
    void accountIsLockedAfterTooManyFailures() throws Exception {
        for (int attempt = 0; attempt < maxPerAccount; attempt++) {
            login("198.51.100.10", "admin@limite.mx", "Incorrecta-" + attempt)
                    .andExpect(status().isUnauthorized());
        }

        login("198.51.100.10", "admin@limite.mx", PASSWORD)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(cookie().doesNotExist("access_token"))
                .andExpect(jsonPath("$.title").value("Demasiados intentos"))
                .andExpect(jsonPath("$.detail").value(containsString("Intenta de nuevo en 15 minutos.")))
                .andExpect(content().string(not(containsString(PASSWORD))));

        // Cambiar de IP no levanta el bloqueo de la cuenta.
        login("198.51.100.11", "admin@limite.mx", PASSWORD)
                .andExpect(status().isTooManyRequests());
        // Ni variar mayusculas en el correo.
        login("198.51.100.12", "ADMIN@limite.mx", PASSWORD)
                .andExpect(status().isTooManyRequests());
    }

    @Test
    @DisplayName("P10: una cuenta inexistente se bloquea igual, sin revelar que no existe")
    void unknownAccountIsThrottledTheSameWay() throws Exception {
        for (int attempt = 0; attempt < maxPerAccount; attempt++) {
            login("198.51.100.20", "fantasma@limite.mx", "Incorrecta-" + attempt)
                    .andExpect(status().isUnauthorized());
        }

        login("198.51.100.20", "fantasma@limite.mx", PASSWORD)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.title").value("Demasiados intentos"));
    }

    @Test
    @DisplayName("P11: muchas cuentas distintas desde la misma IP agotan el limite por IP")
    void sprayingFromOneIpIsThrottled() throws Exception {
        for (int attempt = 0; attempt < maxPerIp; attempt++) {
            login("198.51.100.30", "cuenta" + attempt + "@limite.mx", "Incorrecta-1")
                    .andExpect(status().isUnauthorized());
        }

        // Una cuenta real y sin fallos propios queda bloqueada desde esa IP...
        login("198.51.100.30", "admin@limite.mx", PASSWORD)
                .andExpect(status().isTooManyRequests());
        // ...pero sigue entrando desde cualquier otra.
        login("198.51.100.31", "admin@limite.mx", PASSWORD)
                .andExpect(status().isOk())
                .andExpect(cookie().exists("access_token"));
    }

    @Test
    @DisplayName("un acierto antes del limite reinicia el contador de la cuenta")
    void successResetsTheAccountCounter() throws Exception {
        for (int attempt = 0; attempt < maxPerAccount - 1; attempt++) {
            login("198.51.100.40", "admin@limite.mx", "Incorrecta-" + attempt)
                    .andExpect(status().isUnauthorized());
        }
        login("198.51.100.40", "admin@limite.mx", PASSWORD).andExpect(status().isOk());

        login("198.51.100.40", "admin@limite.mx", "Incorrecta-x")
                .andExpect(status().isUnauthorized());
        login("198.51.100.40", "admin@limite.mx", PASSWORD).andExpect(status().isOk());
    }

    @Test
    @DisplayName("un 400 por formato invalido no cuenta como intento fallido")
    void validationErrorsDoNotCount() throws Exception {
        for (int attempt = 0; attempt < maxPerAccount + 2; attempt++) {
            login("198.51.100.50", "admin@limite.mx", "").andExpect(status().isBadRequest());
        }
        login("198.51.100.50", "admin@limite.mx", PASSWORD).andExpect(status().isOk());
    }

    private static String viaFrontend(String browser) {
        return browser + ":51234, " + FRONTEND_OUTBOUND + ":40000";
    }

    @Test
    @DisplayName("CAL-169: los fallos de un cliente en Azure no bloquean a otro, por la web ni directo")
    void failuresBehindAppServiceOnlyBlockThatClient() throws Exception {
        String attacker = "198.51.100.60";
        for (int attempt = 0; attempt < maxPerIp; attempt++) {
            login(APP_SERVICE_HOP, viaFrontend(attacker), "cuenta" + attempt + "@limite.mx", "Incorrecta-1")
                    .andExpect(status().isUnauthorized());
        }

        // El cliente que fallo queda bloqueado, entre por la web o directo a la API...
        login(APP_SERVICE_HOP, viaFrontend(attacker), "otra@limite.mx", "Incorrecta-1")
                .andExpect(status().isTooManyRequests());
        login(APP_SERVICE_HOP, attacker + ":50000", "otra@limite.mx", "Incorrecta-1")
                .andExpect(status().isTooManyRequests());
        // ...y una cabecera inventada a la izquierda no lo libera.
        login(APP_SERVICE_HOP, "203.0.113.99, " + viaFrontend(attacker), "otra@limite.mx", "Incorrecta-1")
                .andExpect(status().isTooManyRequests());

        // Otro cliente, en otra red, entra con su contrasena por las dos rutas.
        login(APP_SERVICE_HOP, viaFrontend("198.51.100.61"), "admin@limite.mx", PASSWORD)
                .andExpect(status().isOk())
                .andExpect(cookie().exists("access_token"));
        login(APP_SERVICE_HOP, "198.51.100.62:50000", "admin@limite.mx", PASSWORD)
                .andExpect(status().isOk());
    }

    /**
     * Lo que fallo en Azure despues del primer arreglo: el frontend llega a la
     * API desde una salida que no esta en la lista de proxies de confianza.
     * Con el secreto, la llave es la IP que reenvia el middleware de Next.
     */
    @Test
    @DisplayName("CAL-169: con el secreto del frontend, cada navegador tiene su llave aunque la salida no este en la lista")
    void proxySecretKeysOnTheBrowserWhateverTheFrontendOutbound() throws Exception {
        String unlistedOutbound = "203.0.113.200";
        String attacker = "198.51.100.70";
        for (int attempt = 0; attempt < maxPerIp; attempt++) {
            loginThroughNext(unlistedOutbound, attacker, PROXY_SECRET, "cuenta" + attempt + "@limite.mx", "Incorrecta-1")
                    .andExpect(status().isUnauthorized());
        }
        loginThroughNext(unlistedOutbound, attacker, PROXY_SECRET, "otra@limite.mx", "Incorrecta-1")
                .andExpect(status().isTooManyRequests());
        // El mismo cliente directo a la API comparte la llave.
        login(APP_SERVICE_HOP, attacker + ":50000", "otra@limite.mx", "Incorrecta-1")
                .andExpect(status().isTooManyRequests());
        // Inventar la IP sin el secreto correcto no lo libera.
        loginThroughNext(attacker, "198.51.100.79", "secreto-inventado-de-al-menos-32-bytes",
                "otra@limite.mx", "Incorrecta-1")
                .andExpect(status().isTooManyRequests());

        // Otro navegador por la misma salida del frontend entra con su contrasena.
        loginThroughNext(unlistedOutbound, "198.51.100.71", PROXY_SECRET, "admin@limite.mx", PASSWORD)
                .andExpect(status().isOk())
                .andExpect(cookie().exists("access_token"));
    }

    /** Navegador → Next (reenvia su IP con el secreto) → App Service de la API. */
    private ResultActions loginThroughNext(String frontendOutbound, String browser, String secret,
                                           String email, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                .with(request -> {
                    request.setRemoteAddr(APP_SERVICE_HOP);
                    request.addHeader("X-Forwarded-For", browser + ":51234, " + frontendOutbound + ":40000");
                    request.addHeader("X-Callejon9-Client-Ip", browser);
                    request.addHeader("X-Callejon9-Proxy-Secret", secret);
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"slug":"%s","email":"%s","password":"%s"}
                        """.formatted(slug, email, password)));
    }
}
