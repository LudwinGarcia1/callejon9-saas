package com.callejon9.order.service;

import java.util.UUID;

/**
 * Quien ejecuta una operacion sobre una orden. Sale siempre de la sesion
 * autenticada (ver TenantFilter), nunca del body: el rol se usa para decidir
 * si el usuario puede operar sobre ordenes que no abrio.
 */
public record OrderActor(UUID userId, boolean admin) {
}
