package com.callejon9.user.web.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateUserPasswordRequest(
        @NotBlank(message = "Ingresa una contrasena.") String password) {
}
