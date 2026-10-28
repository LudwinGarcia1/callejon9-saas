package com.callejon9.shared.time;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Aplica {@link RestaurantTimeZones#isSupported(String)} a un campo de texto. */
@Documented
@Constraint(validatedBy = RestaurantTimeZoneValidator.class)
@Target({ElementType.FIELD, ElementType.RECORD_COMPONENT, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface RestaurantTimeZone {

    String message() default "Debe ser una zona horaria IANA geografica, por ejemplo America/Cancun.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
