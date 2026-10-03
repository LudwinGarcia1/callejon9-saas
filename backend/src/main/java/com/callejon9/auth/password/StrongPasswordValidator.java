package com.callejon9.auth.password;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class StrongPasswordValidator
        implements ConstraintValidator<StrongPassword, PasswordCandidate> {

    @Override
    public boolean isValid(PasswordCandidate candidate, ConstraintValidatorContext context) {
        // Una contrasena vacia ya la reporta @NotBlank en el campo; repetir el
        // error aqui solo sobrescribiria ese mensaje con otro menos claro.
        if (candidate == null || candidate.password() == null || candidate.password().isBlank()) {
            return true;
        }

        return PasswordPolicy.check(candidate.password(), candidate.personalData())
                .map(message -> {
                    context.disableDefaultConstraintViolation();
                    context.buildConstraintViolationWithTemplate(message)
                            .addPropertyNode("password")
                            .addConstraintViolation();
                    return false;
                })
                .orElse(true);
    }
}
