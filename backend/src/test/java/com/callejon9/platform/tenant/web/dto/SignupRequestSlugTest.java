package com.callejon9.platform.tenant.web.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reglas del identificador (slug) del alta de restaurante: {@code @NotBlank} y
 * {@code @Pattern("^[a-z0-9-]{3,80}$")} de {@link SignupRequest}. Solo se valida
 * la propiedad: la politica de contrasenas (a nivel de clase) tiene su propia
 * prueba en {@code SignupPasswordPolicyTest}.
 */
@DisplayName("UT · identificador del alta de restaurante")
class SignupRequestSlugTest {

    private static final String PATTERN_MESSAGE = "Solo minusculas, numeros y guiones, entre 3 y 80 caracteres.";

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void createValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        factory.close();
    }

    private static Set<String> messagesFor(String slug) {
        Set<ConstraintViolation<SignupRequest>> violations =
                validator.validateValue(SignupRequest.class, "slug", slug);
        return violations.stream().map(ConstraintViolation::getMessage).collect(Collectors.toSet());
    }

    @ParameterizedTest(name = "«{0}» se acepta")
    @ValueSource(strings = {"abc", "la-esquina", "cocina-2", "123"})
    @DisplayName("Válido: minúsculas, números y guiones")
    void acceptsValidSlugs(String slug) {
        assertThat(messagesFor(slug)).isEmpty();
    }

    @ParameterizedTest(name = "«{0}» se rechaza")
    @ValueSource(strings = {"ab", "a", "La-Esquina", "la_esquina", "la esquina", "cafe-niño", "la.esquina", " abc", "abc/admin"})
    @DisplayName("Inválido: menos de 3 caracteres, mayúsculas, guion bajo, espacios, acentos y símbolos")
    void rejectsInvalidSlugs(String slug) {
        assertThat(messagesFor(slug)).containsExactly(PATTERN_MESSAGE);
    }

    @Test
    @DisplayName("Los bordes de longitud son exactos: 80 caracteres pasa y 81 no")
    void lengthBoundsAreExact() {
        assertThat(messagesFor("a".repeat(3))).isEmpty();
        assertThat(messagesFor("a".repeat(2))).containsExactly(PATTERN_MESSAGE);
        assertThat(messagesFor("a".repeat(80))).isEmpty();
        assertThat(messagesFor("a".repeat(81))).containsExactly(PATTERN_MESSAGE);
    }

    @Test
    @DisplayName("Vacío o nulo: se rechaza por obligatorio")
    void blankOrNullSlugIsRequired() {
        assertThat(messagesFor(null)).hasSize(1);
        assertThat(messagesFor("")).contains(PATTERN_MESSAGE).hasSize(2);
        assertThat(messagesFor("   ")).contains(PATTERN_MESSAGE).hasSize(2);
    }
}
