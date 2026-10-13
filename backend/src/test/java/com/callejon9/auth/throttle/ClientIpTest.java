package com.callejon9.auth.throttle;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("IP del cliente detras del proxy de Next")
class ClientIpTest {

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
        assertThat(ClientIp.of(request("203.0.113.7", null))).isEqualTo("203.0.113.7");
    }

    @Test
    @DisplayName("detras de Next toma la ultima entrada, la que agrego el proxy")
    void trustsTheLastHopFromALocalProxy() {
        assertThat(ClientIp.of(request("127.0.0.1", "203.0.113.7"))).isEqualTo("203.0.113.7");
        assertThat(ClientIp.of(request("172.18.0.3", "203.0.113.7"))).isEqualTo("203.0.113.7");
        // El cliente invento la primera entrada; la ultima es la real.
        assertThat(ClientIp.of(request("127.0.0.1", "1.2.3.4, 203.0.113.7")))
                .isEqualTo("203.0.113.7");
    }

    @Test
    @DisplayName("ignora la cabecera si la conexion no viene de la red local")
    void ignoresTheHeaderFromAPublicAddress() {
        assertThat(ClientIp.of(request("203.0.113.7", "10.0.0.1"))).isEqualTo("203.0.113.7");
    }
}
