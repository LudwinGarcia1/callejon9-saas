package com.callejon9.user.web.dto;

import com.callejon9.user.domain.UserRole;
import jakarta.validation.constraints.NotNull;

public record UpdateUserRoleRequest(@NotNull(message = "Selecciona un rol.") UserRole role) {
}
