package com.callejon9.shared.time;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class RestaurantTimeZoneValidator implements ConstraintValidator<RestaurantTimeZone, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        // Un valor ausente ya lo reporta @NotBlank en el mismo campo.
        return value == null || value.isBlank() || RestaurantTimeZones.isSupported(value);
    }
}
