package com.callejon9.platform.tenant.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SignupRequest(
        @NotBlank @Size(max = 160) String restaurantName,

        @NotBlank
        @Pattern(regexp = "^[a-z0-9-]{3,80}$",
                 message = "Solo minusculas, numeros y guiones, entre 3 y 80 caracteres.")
        String slug,

        @NotBlank @Email @Size(max = 180) String adminEmail,
        @NotBlank @Size(max = 160) String adminFullName,
        @NotBlank @Size(min = 8, max = 100) String password,
        @NotBlank String planCode) {
}
