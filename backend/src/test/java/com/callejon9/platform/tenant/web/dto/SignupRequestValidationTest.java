package com.callejon9.platform.tenant.web.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("UT · Validación del identificador de alta")
class SignupRequestValidationTest {

    private static final ValidatorFactory VALIDATOR_FACTORY =
            Validation.buildDefaultValidatorFactory();
    private static final Validator VALIDATOR = VALIDATOR_FACTORY.getValidator();

    @AfterAll
    static void closeValidatorFactory() {
        VALIDATOR_FACTORY.close();
    }

    @ParameterizedTest
    @ValueSource(strings = {"ab", "Mi Restaurante", "mi_restaurante", "restaurante!"})
    void rejectsSlugWithInvalidLengthOrCharacters(String slug) {
        assertSlugViolation(slug);
    }

    @Test
    void rejectsSlugLongerThanEightyCharacters() {
        assertSlugViolation("a".repeat(81));
    }

    @ParameterizedTest
    @ValueSource(strings = {"mi-restaurante", "restaurante2026"})
    void acceptsSlugWithLowercaseLettersNumbersAndHyphens(String slug) {
        assertThat(VALIDATOR.validate(request(slug))).isEmpty();
    }

    private static void assertSlugViolation(String slug) {
        Set<?> violations = VALIDATOR.validate(request(slug));
        assertThat(violations)
                .anySatisfy(violation -> {
                    jakarta.validation.ConstraintViolation<?> typedViolation =
                            (jakarta.validation.ConstraintViolation<?>) violation;
                    assertThat(typedViolation.getPropertyPath().toString()).isEqualTo("slug");
                    assertThat(typedViolation.getMessage())
                            .isEqualTo("Solo minusculas, numeros y guiones, entre 3 y 80 caracteres.");
                });
    }

    private static SignupRequest request(String slug) {
        return new SignupRequest(
                "La Barra de Prueba",
                slug,
                "admin@example.test",
                "R. Test",
                "Vaso-Azul-847!",
                "PRO");
    }
}
