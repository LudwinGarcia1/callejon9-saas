package com.callejon9.tenancy;

import com.callejon9.support.TestSessions;
import com.callejon9.user.domain.UserRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ejercita /api/v1/platform/** contra un Tomcat embebido real en un puerto
 * aleatorio, en vez de MockMvc.
 *
 * <p>{@code TenantFilterTest.adminIsForbiddenFromPlatformEndpoints()} pasa
 * con MockMvc y aun asi el servidor real respondia 401 en vez de 403 para un
 * ADMIN autenticado: {@code MockHttpServletResponse.sendError(...)} solo
 * anota el codigo de estado, mientras que en un Tomcat real dispara un
 * segundo despacho interno (DispatcherType.ERROR) hacia "/error" que
 * atraviesa de nuevo toda la cadena de filtros de Spring Security. Esa
 * segunda pasada es la que decidia el 401 (ver el javadoc de
 * {@link TenantFilter#shouldNotFilterErrorDispatch()}). MockMvc nunca
 * reproduce ese segundo despacho, asi que ninguna prueba basada en MockMvc
 * puede detectar una regresion en ese mecanismo — de ahi que esta prueba
 * levante un servidor real y hable HTTP de verdad.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DisplayName("Autorizacion de /api/v1/platform/** sobre un servidor real")
class TenantFilterHttpTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    private static final String SLUG_PREFIX = "filtro-http-test";

    @Autowired
    private TestSessions testSessions;

    @AfterEach
    void cleanUp() {
        testSessions.deleteTenants(SLUG_PREFIX);
    }

    private String tokenFor(UserRole role) {
        return testSessions.accessTokenForNewUser(SLUG_PREFIX, role);
    }

    private ResponseEntity<String> platformPlans(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        if (accessToken != null) {
            headers.add(HttpHeaders.COOKIE, TenantFilter.ACCESS_TOKEN_COOKIE + "=" + accessToken);
        }
        return restTemplate.exchange(
                "http://localhost:" + port + "/api/v1/platform/plans",
                HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    @Test
    @DisplayName("un ADMIN autenticado recibe 403, no 401")
    void adminIsForbiddenFromPlatformEndpointsOnARealServer() {
        ResponseEntity<String> response = platformPlans(tokenFor(UserRole.ADMIN));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("sin cookie la peticion sigue siendo 401")
    void requestWithoutTokenIsUnauthorizedOnARealServer() {
        ResponseEntity<String> response = platformPlans(null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("un SUPER_ADMIN sigue recibiendo 200 con los planes")
    void superAdminIsAllowedOnARealServer() {
        ResponseEntity<String> response = platformPlans(tokenFor(UserRole.SUPER_ADMIN));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("FREE", "PRO", "PREMIUM");
    }
}
