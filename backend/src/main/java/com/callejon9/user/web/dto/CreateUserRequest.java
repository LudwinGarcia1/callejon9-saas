package com.callejon9.user.web.dto;

import com.callejon9.auth.password.PasswordCandidate;
import com.callejon9.auth.password.StrongPassword;
import com.callejon9.user.domain.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Arrays;
import java.util.List;

/** La contrasena se valida con {@link StrongPassword}; ver {@code PasswordPolicy}. */
@StrongPassword
public record CreateUserRequest(
        @NotBlank @Email @Size(max = 180) String email,
        @NotBlank @Size(max = 160) String fullName,
        @NotNull UserRole role,
        @NotBlank(message = "Ingresa una contrasena.") String password) implements PasswordCandidate {

    @Override
    public List<String> personalData() {
        return Arrays.asList(email, fullName);
    }
}
