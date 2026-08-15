package com.callejon9.inventory.web.dto;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Regla cruzada entre {@code movementType} y los campos de cantidad: no cabe
 * en una anotacion por campo porque depende del valor de otro campo.
 *
 * Va en la capa de validacion, no en el servicio: mandar el campo equivocado
 * es una solicitud mal formada (400), no un conflicto de negocio (409).
 */
@Documented
@Constraint(validatedBy = MovementRequestValidator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidMovementRequest {

    String message() default "La combinacion de tipo y campos del movimiento es invalida.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
