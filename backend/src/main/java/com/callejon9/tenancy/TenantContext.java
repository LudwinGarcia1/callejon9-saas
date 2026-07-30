package com.callejon9.tenancy;

import java.util.UUID;

/**
 * Mantiene el tenant activo durante el ciclo de vida de una peticion.
 *
 * Equivale al {@code utils/tenant_context.py} del sistema Flask original, que
 * usaba contextvars. Aqui se usa un ThreadLocal, y como Spring MVC atiende cada
 * peticion en su propio hilo, el aislamiento entre peticiones es el mismo.
 */
public final class TenantContext {

    private static final ThreadLocal<UUID> CURRENT_TENANT = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void set(UUID tenantId) {
        CURRENT_TENANT.set(tenantId);
    }

    public static UUID require() {
        UUID tenantId = CURRENT_TENANT.get();
        if (tenantId == null) {
            throw new NoTenantContextException();
        }
        return tenantId;
    }

    public static UUID currentOrNull() {
        return CURRENT_TENANT.get();
    }

    public static void clear() {
        CURRENT_TENANT.remove();
    }
}
