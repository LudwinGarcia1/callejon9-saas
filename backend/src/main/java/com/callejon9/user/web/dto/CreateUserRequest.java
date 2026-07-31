package com.callejon9.user.web.dto;

import com.callejon9.user.domain.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateUserRequest(
        @NotBlank @Email @Size(max = 180) String email,
        @NotBlank @Size(max = 160) String fullName,
        @NotNull UserRole role,
        @NotBlank @Size(min = 8, max = 100) String password) {
}
