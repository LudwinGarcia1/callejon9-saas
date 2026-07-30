package com.callejon9.realtime;

import com.callejon9.user.domain.UserRole;
import java.security.Principal;
import java.util.UUID;

/**
 * El Principal que queda asociado a la sesion de WebSocket tras un handshake
 * exitoso (ver {@link JwtHandshakeInterceptor}). A diferencia de HTTP, donde
 * TenantFilter fija el TenantContext por peticion, aqui el tenant viaja
 * pegado al Principal de la sesion para que {@link TenantSubscriptionInterceptor}
 * lo pueda validar en cada SUBSCRIBE sin volver a tocar la base de datos.
 */
public record AuthenticatedPrincipal(UUID userId, UUID tenantId, UserRole role) implements Principal {

    @Override
    public String getName() {
        return userId.toString();
    }
}
