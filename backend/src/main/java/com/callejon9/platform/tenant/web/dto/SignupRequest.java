package com.callejon9.platform.tenant.web.dto;

import com.callejon9.auth.password.PasswordCandidate;
import com.callejon9.auth.password.StrongPassword;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Arrays;
import java.util.List;

/** La contrasena se valida con {@link StrongPassword}; ver {@code PasswordPolicy}. */
@StrongPassword
public record SignupRequest(
        @NotBlank @Size(max = 160) String restaurantName,

        @NotBlank
        @Pattern(regexp = "^[a-z0-9-]{3,80}$",
                 message = "Solo minusculas, numeros y guiones, entre 3 y 80 caracteres.")
        String slug,

        @NotBlank @Email @Size(max = 180) String adminEmail,
        @NotBlank @Size(max = 160) String adminFullName,
        @NotBlank(message = "Ingresa una contrasena.") String password,
        @NotBlank String planCode) implements PasswordCandidate {

    @Override
    public List<String> personalData() {
        return Arrays.asList(restaurantName, slug, adminEmail, adminFullName);
    }
}
