package com.callejon9.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuracion de los JWT de sesion ({@code app.jwt.*}).
 *
 * <p>El secreto solo se valida aqui como no vacio. La longitud minima y los
 * valores de desarrollo los rechaza {@link ProductionConfigurationGuard} con un
 * mensaje propio: una anotacion como {@code @Size} haria que Spring imprimiera
 * el valor rechazado en el error de arranque, y ese valor es el secreto.
 */
@Validated
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
        @NotBlank String secret,
        @Positive long accessTokenMinutes,
        @Positive long refreshTokenDays) {

    @Override
    public String toString() {
        return "JwtProperties[secret=****, accessTokenMinutes=" + accessTokenMinutes
                + ", refreshTokenDays=" + refreshTokenDays + "]";
    }
}
