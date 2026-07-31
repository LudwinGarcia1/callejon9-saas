package com.callejon9.user.web.dto;

import com.callejon9.user.domain.User;
import com.callejon9.user.domain.UserRole;
import java.util.UUID;

/**
 * Nunca incluye passwordHash ni totpSecret: son datos internos de
 * autenticacion, no informacion que el frontend deba ver.
 */
public record UserResponse(UUID id, String email, String fullName, UserRole role, boolean active) {

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(), user.getEmail(), user.getFullName(), user.getRole(), user.isActive());
    }
}
