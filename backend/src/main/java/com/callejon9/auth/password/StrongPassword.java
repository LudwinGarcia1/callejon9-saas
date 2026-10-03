package com.callejon9.auth.password;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Aplica {@link PasswordPolicy} a un {@link PasswordCandidate}. Es una
 * restriccion de clase -- no de campo -- porque la regla de datos personales
 * necesita ver el correo y el nombre ademas de la contrasena. El error se
 * reporta sobre el campo {@code password}, asi que el cliente lo recibe en
 * {@code errors.password} igual que cualquier otra validacion.
 */
@Documented
@Constraint(validatedBy = StrongPasswordValidator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface StrongPassword {

    String message() default "La contrasena no cumple la politica de seguridad.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
