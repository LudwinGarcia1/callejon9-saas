package com.callejon9.auth.web.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * El slug identifica el restaurante. Es obligatorio porque el correo solo es
 * unico dentro de un tenant: sin el, no se sabria en que restaurante buscar.
 */
public record LoginRequest(
        @NotBlank String slug,
        @NotBlank String email,
        @NotBlank String password) {
}
