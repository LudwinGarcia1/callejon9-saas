package com.callejon9.order;

import com.callejon9.order.service.WaiterCallThrottle;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Límite de avisos de «Llamar al mesero»")
class WaiterCallThrottleTest {

    private final WaiterCallThrottle throttle = new WaiterCallThrottle();
    private final UUID tenantA = UUID.randomUUID();
    private final UUID tenantB = UUID.randomUUID();
    private final Instant start = Instant.parse("2026-11-28T20:00:00Z");

    @Test
    @DisplayName("la misma mesa avisa una vez por ventana y vuelve a avisar al vencerse")
    void sameTableNotifiesOncePerWindow() {
        assertThat(throttle.tryAcquire(tenantA, 5, start)).isTrue();
        assertThat(throttle.tryAcquire(tenantA, 5, start.plusSeconds(5))).isFalse();
        assertThat(throttle.tryAcquire(tenantA, 5, start.plus(WaiterCallThrottle.WINDOW).minusMillis(1))).isFalse();
        assertThat(throttle.tryAcquire(tenantA, 5, start.plus(WaiterCallThrottle.WINDOW))).isTrue();
    }

    @Test
    @DisplayName("otra mesa u otro restaurante no comparten la ventana")
    void windowIsPerTenantAndTable() {
        assertThat(throttle.tryAcquire(tenantA, 5, start)).isTrue();
        assertThat(throttle.tryAcquire(tenantA, 6, start)).isTrue();
        assertThat(throttle.tryAcquire(tenantB, 5, start)).isTrue();
    }
}
