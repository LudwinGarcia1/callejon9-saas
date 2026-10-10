package com.callejon9.platform.tenant.throttle;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Llave de cupo del registro de restaurantes")
class SignupRateLimiterTest {

    @Test
    @DisplayName("una IPv4 cuenta tal cual")
    void ipv4IsUsedAsIs() {
        assertThat(SignupRateLimiter.keyFor("203.0.113.7")).isEqualTo("203.0.113.7");
    }

    @Test
    @DisplayName("las direcciones IPv6 de una misma red /64 comparten llave")
    void ipv6AddressesInTheSameSlash64ShareTheKey() {
        String first = SignupRateLimiter.keyFor("2001:db8:1:2::1");

        assertThat(first).isEqualTo("2001:db8:1:2:0:0:0:0/64");
        assertThat(SignupRateLimiter.keyFor("2001:db8:1:2:ffff:ffff:ffff:ffff")).isEqualTo(first);
        assertThat(SignupRateLimiter.keyFor("2001:DB8:1:2:0:0:0:abc")).isEqualTo(first);
    }

    @Test
    @DisplayName("otra red /64 tiene su propia llave")
    void differentSlash64sHaveDifferentKeys() {
        assertThat(SignupRateLimiter.keyFor("2001:db8:1:3::1"))
                .isNotEqualTo(SignupRateLimiter.keyFor("2001:db8:1:2::1"));
    }

    @Test
    @DisplayName("una IPv6 que encapsula una IPv4 cuenta como esa IPv4")
    void ipv4MappedAddressCountsAsIpv4() {
        assertThat(SignupRateLimiter.keyFor("::ffff:203.0.113.7")).isEqualTo("203.0.113.7");
    }

    @Test
    @DisplayName("un valor que no es IP se usa tal cual, sin consultar DNS")
    void nonAddressValuesAreUsedAsIs() {
        assertThat(SignupRateLimiter.keyFor("no:es:una:ip")).isEqualTo("no:es:una:ip");
        assertThat(SignupRateLimiter.keyFor("proxy.interno")).isEqualTo("proxy.interno");
    }
}
