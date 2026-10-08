package com.callejon9.tenancy;

import java.util.UUID;
import java.util.function.Supplier;

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

    /**
     * Ejecuta {@code action} con {@code tenantId} como tenant activo y deja el
     * contexto exactamente como estaba, incluso si la accion falla. Sirve para
     * operar con un tenant que viene firmado en un token mientras la peticion
     * ya tenia otro publicado (o ninguno).
     */
    public static <T> T callAs(UUID tenantId, Supplier<T> action) {
        UUID previous = CURRENT_TENANT.get();
        CURRENT_TENANT.set(tenantId);
        try {
            return action.get();
        } finally {
            if (previous == null) {
                CURRENT_TENANT.remove();
            } else {
                CURRENT_TENANT.set(previous);
            }
        }
    }
}
