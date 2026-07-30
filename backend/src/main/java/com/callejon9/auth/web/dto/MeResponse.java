package com.callejon9.auth.web.dto;

import java.util.UUID;

/** Identidad del usuario autenticado, resuelta a partir de la cookie httpOnly. */
public record MeResponse(
        UUID userId,
        String fullName,
        String role,
        UUID tenantId,
        String slug,
        String restaurantName) {
}
