package com.callejon9.auth.throttle;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

@DisplayName("IP del cliente detras del proxy de Next")
class ClientIpTest {

    /** Direccion desde la que el front end de App Service entrega al contenedor. */
    private static final String APP_SERVICE_HOP = "169.254.129.3";
    /** IP de salida simulada del App Service del frontend. */
    private static final String FRONTEND_OUTBOUND = "20.1.2.3";

    private final ClientIp clientIp = new ClientIp(List.of());
    private final ClientIp behindFrontend = new ClientIp(List.of(FRONTEND_OUTBOUND));

    private static MockHttpServletRequest request(String remoteAddress, String forwardedFor) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(remoteAddress);
        if (forwardedFor != null) {
            request.addHeader(ClientIp.FORWARDED_FOR, forwardedFor);
        }
        return request;
    }

    @Test
    void usesTheConnectionAddressWithoutHeader() {
        assertThat(clientIp.of(request("203.0.113.7", null))).isEqualTo("203.0.113.7");
    }

    @Test
    @DisplayName("detras de Next toma la ultima entrada, la que agrego el proxy")
    void trustsTheLastHopFromALocalProxy() {
        assertThat(clientIp.of(request("127.0.0.1", "203.0.113.7"))).isEqualTo("203.0.113.7");
        assertThat(clientIp.of(request("172.18.0.3", "203.0.113.7"))).isEqualTo("203.0.113.7");
        // El cliente invento la primera entrada; la ultima es la real.
        assertThat(clientIp.of(request("127.0.0.1", "1.2.3.4, 203.0.113.7")))
                .isEqualTo("203.0.113.7");
    }

    @Test
    @DisplayName("ignora la cabecera si la conexion no viene de un proxy de confianza")
    void ignoresTheHeaderFromAPublicAddress() {
        assertThat(clientIp.of(request("203.0.113.7", "10.0.0.1"))).isEqualTo("203.0.113.7");
        assertThat(behindFrontend.of(request("203.0.113.7", "198.51.100.7"))).isEqualTo("203.0.113.7");
    }

    @Test
    @DisplayName("quita el puerto que Azure App Service agrega a cada entrada (ip:puerto)")
    void stripsThePortAddedByAppService() {
        assertThat(clientIp.of(request("10.0.0.5", "203.0.113.7:51234"))).isEqualTo("203.0.113.7");
        assertThat(clientIp.of(request("10.0.0.5", "1.2.3.4, 203.0.113.7:443"))).isEqualTo("203.0.113.7");
    }

    @Test
    @DisplayName("dos conexiones del mismo cliente con puertos distintos dan la misma llave")
    void sameClientDifferentPortsShareTheKey() {
        assertThat(clientIp.of(request("10.0.0.5", "203.0.113.7:51234")))
                .isEqualTo(clientIp.of(request("10.0.0.5", "203.0.113.7:51999")));
    }

    @Test
    @DisplayName("IPv6: quita el puerto entre corchetes y deja intacta una direccion sin puerto")
    void handlesIpv6WithAndWithoutPort() {
        assertThat(clientIp.of(request("10.0.0.5", "[2001:db8::7]:51234"))).isEqualTo("2001:db8::7");
        assertThat(clientIp.of(request("10.0.0.5", "2001:db8::7"))).isEqualTo("2001:db8::7");
    }

    @Test
    @DisplayName("una entrada final vacia o solo con puerto cae a la direccion de la conexion")
    void blankOrPortOnlyLastHopFallsBackToTheConnection() {
        assertThat(clientIp.of(request("10.0.0.5", "203.0.113.7, "))).isEqualTo("10.0.0.5");
        assertThat(clientIp.of(request("10.0.0.5", ":51234"))).isEqualTo("10.0.0.5");
    }

    @Test
    @DisplayName("CAL-169: directo a la API en App Service cada cliente tiene su propia llave")
    void appServiceLinkLocalHopIsTrusted() {
        assertThat(clientIp.of(request(APP_SERVICE_HOP, "203.0.113.7:51234"))).isEqualTo("203.0.113.7");
        assertThat(clientIp.of(request(APP_SERVICE_HOP, "198.51.100.9:40000"))).isEqualTo("198.51.100.9");
    }

    @Test
    @DisplayName("CAL-169: por el frontend salta la IP de salida de Next y toma la del navegador")
    void skipsTheConfiguredFrontendOutboundAddress() {
        String viaFrontend = "203.0.113.7:51234, " + FRONTEND_OUTBOUND + ":40000";

        assertThat(behindFrontend.of(request(APP_SERVICE_HOP, viaFrontend))).isEqualTo("203.0.113.7");
        // Sin configurarla, todos los usuarios de la web compartirian esa IP.
        assertThat(clientIp.of(request(APP_SERVICE_HOP, viaFrontend))).isEqualTo(FRONTEND_OUTBOUND);
    }

    @Test
    @DisplayName("CAL-169: una entrada inventada a la izquierda del navegador no cambia la llave")
    void spoofedEntriesLeftOfTheClientAreIgnored() {
        String spoofed = "6.6.6.6, 10.0.0.1, 203.0.113.7:51234, " + FRONTEND_OUTBOUND + ":40000";

        assertThat(behindFrontend.of(request(APP_SERVICE_HOP, spoofed))).isEqualTo("203.0.113.7");
        // Directo a la API, App Service agrega la IP real al final.
        assertThat(clientIp.of(request(APP_SERVICE_HOP, "6.6.6.6, 203.0.113.7:51234")))
                .isEqualTo("203.0.113.7");
    }

    @Test
    @DisplayName("los proxies de confianza aceptan rangos CIDR en IPv4 e IPv6")
    void trustedProxiesAcceptCidrRanges() {
        ClientIp ranges = new ClientIp(List.of(" 20.1.2.0/24 ", "2001:db8:aa::/48", ""));

        assertThat(ranges.of(request(APP_SERVICE_HOP, "203.0.113.7, 20.1.2.200"))).isEqualTo("203.0.113.7");
        assertThat(ranges.of(request(APP_SERVICE_HOP, "203.0.113.7, 20.1.3.1"))).isEqualTo("20.1.3.1");
        assertThat(ranges.of(request(APP_SERVICE_HOP, "203.0.113.7, [2001:db8:aa:1::5]:443")))
                .isEqualTo("203.0.113.7");
        assertThat(ranges.of(request(APP_SERVICE_HOP, "203.0.113.7, 2001:db8:ab::5")))
                .isEqualTo("2001:db8:ab::5");
    }

    @Test
    @DisplayName("una entrada que no es IP no se resuelve por DNS y cuenta el ultimo salto valido")
    void hostNamesInTheHeaderAreNotResolved() {
        assertThat(clientIp.of(request("10.0.0.5", "localhost"))).isEqualTo("10.0.0.5");
        assertThat(behindFrontend.of(request(APP_SERVICE_HOP, "evil.example, " + FRONTEND_OUTBOUND)))
                .isEqualTo(FRONTEND_OUTBOUND);
    }

    @Test
    @DisplayName("si toda la cadena es de confianza, el cliente es la entrada mas lejana")
    void allTrustedChainUsesTheFarthestHop() {
        assertThat(clientIp.of(request("10.0.0.5", "172.18.0.4, 10.0.0.9"))).isEqualTo("172.18.0.4");
    }

    @Test
    @DisplayName("una entrada mal escrita en la configuracion detiene el arranque")
    void rejectsInvalidTrustedProxies() {
        assertThatIllegalArgumentException().isThrownBy(() -> new ClientIp(List.of("frontend.example")))
                .withMessageContaining("frontend.example");
        assertThatIllegalArgumentException().isThrownBy(() -> new ClientIp(List.of("20.1.2.0/33")));
        assertThatIllegalArgumentException().isThrownBy(() -> new ClientIp(List.of("20.1.2.0/x")));
    }
}
