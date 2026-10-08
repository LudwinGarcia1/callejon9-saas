package com.callejon9.tenancy;

import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TenantContextTest {

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void requireReturnsTheTenantThatWasSet() {
        UUID tenantId = UUID.randomUUID();

        TenantContext.set(tenantId);

        assertThat(TenantContext.require()).isEqualTo(tenantId);
    }

    @Test
    void requireFailsClosedWhenNoTenantIsSet() {
        assertThatThrownBy(TenantContext::require)
                .isInstanceOf(NoTenantContextException.class)
                .hasMessageContaining("restaurante identificado");
    }

    @Test
    void currentOrNullReturnsNullInsteadOfThrowing() {
        assertThat(TenantContext.currentOrNull()).isNull();
    }

    @Test
    void clearRemovesTheTenant() {
        TenantContext.set(UUID.randomUUID());

        TenantContext.clear();

        assertThat(TenantContext.currentOrNull()).isNull();
    }

    @Test
    void tenantDoesNotLeakToAnotherThread() throws Exception {
        TenantContext.set(UUID.randomUUID());

        UUID[] seenByOtherThread = new UUID[1];
        Thread other = new Thread(() -> seenByOtherThread[0] = TenantContext.currentOrNull());
        other.start();
        other.join();

        assertThat(seenByOtherThread[0]).isNull();
    }

    @Test
    void callAsRestoresThePreviousTenant() {
        UUID requestTenant = UUID.randomUUID();
        UUID tokenTenant = UUID.randomUUID();
        TenantContext.set(requestTenant);

        UUID seenInside = TenantContext.callAs(tokenTenant, TenantContext::require);

        assertThat(seenInside).isEqualTo(tokenTenant);
        assertThat(TenantContext.require()).isEqualTo(requestTenant);
    }

    @Test
    void callAsLeavesNoTenantBehindWhenThereWasNone() {
        TenantContext.callAs(UUID.randomUUID(), TenantContext::require);

        assertThat(TenantContext.currentOrNull()).isNull();
    }

    @Test
    void callAsRestoresTheContextEvenWhenTheActionFails() {
        UUID requestTenant = UUID.randomUUID();
        TenantContext.set(requestTenant);

        assertThatThrownBy(() -> TenantContext.callAs(UUID.randomUUID(), () -> {
            throw new IllegalStateException("falla dentro");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(TenantContext.require()).isEqualTo(requestTenant);
    }
}
