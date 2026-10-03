package com.callejon9.auth.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * El slug identifica el restaurante. Es obligatorio porque el correo solo es
 * unico dentro de un tenant: sin el, no se sabria en que restaurante buscar.
 *
 * <p>Los limites replican los de {@code SignupRequest}: un valor que el
 * registro nunca habria aceptado se rechaza aqui con 400, antes de consultar
 * la base de datos o calcular un hash de bcrypt. La contrasena no exige
 * longitud minima para no filtrar la politica de contrasenas en el login;
 * solo se acota el maximo.
 */
public record LoginRequest(
        @NotBlank(message = "Ingresa el identificador del restaurante.")
        @Pattern(regexp = "^[a-z0-9-]{3,80}$",
                 message = "Solo minusculas, numeros y guiones, entre 3 y 80 caracteres.")
        String slug,

        @NotBlank(message = "Ingresa tu correo.")
        @Email(message = "Ingresa un correo valido.")
        @Size(max = 180, message = "El correo no puede exceder 180 caracteres.")
        String email,

        @NotBlank(message = "Ingresa tu contrasena.")
        @Size(max = 100, message = "La contrasena no puede exceder 100 caracteres.")
        String password) {
}
