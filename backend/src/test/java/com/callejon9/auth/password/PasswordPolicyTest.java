package com.callejon9.auth.password;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Politica de contrasenas")
class PasswordPolicyTest {

    private static final List<String> NONE = List.of();

    @ParameterizedTest
    @ValueSource(strings = {"Mantel-Azul-47", "tortuga verde 2031", "Lámpara8Río3", "x7Kq!pz2Lw"})
    void acceptsReasonablePasswords(String password) {
        assertThat(PasswordPolicy.check(password, NONE)).isEmpty();
    }

    @Test
    void rejectsShortPasswords() {
        assertThat(PasswordPolicy.check("Ab1cdefgh", NONE)).contains(PasswordPolicy.TOO_SHORT);
    }

    @Test
    @DisplayName("mide el maximo en bytes porque bcrypt ignora lo que pasa de 72")
    void rejectsPasswordsOverBcryptLimit() {
        assertThat(PasswordPolicy.check("a1b2c" + "x".repeat(67), NONE)).isEmpty();
        assertThat(PasswordPolicy.check("a1b2c" + "x".repeat(68), NONE))
                .contains(PasswordPolicy.TOO_LONG);
        // 38 caracteres, pero 'ñ' ocupa dos bytes: 72 + 2 = 74 bytes.
        assertThat(PasswordPolicy.check("a1" + "ñ".repeat(36), NONE))
                .contains(PasswordPolicy.TOO_LONG);
    }

    @ParameterizedTest
    @ValueSource(strings = {"solamenteletras", "12345678901234", "!!!!????----"})
    void requiresLettersAndDigits(String password) {
        assertThat(PasswordPolicy.check(password, NONE))
                .contains(PasswordPolicy.MISSING_LETTER_OR_DIGIT);
    }

    @Test
    void rejectsRepetitivePasswords() {
        assertThat(PasswordPolicy.check("aaaaaaaaa1", NONE)).contains(PasswordPolicy.TOO_REPETITIVE);
        assertThat(PasswordPolicy.check("abab1212abab", NONE)).contains(PasswordPolicy.TOO_REPETITIVE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Password2024!", "qwerty123456", "Contraseña123", "restaurante1",
            "Secreto123!", "1q2w3e4r5t", "Callejon9-2026"})
    void rejectsCommonPasswordsAndTheirSuffixVariants(String password) {
        assertThat(PasswordPolicy.check(password, NONE)).contains(PasswordPolicy.TOO_COMMON);
    }

    @Test
    @DisplayName("rechaza contrasenas con el nombre, el correo o el identificador del restaurante")
    void rejectsPersonalData() {
        List<String> account = List.of("La Esquina", "la-esquina", "jperez@correo.mx", "Juan Pérez");

        assertThat(PasswordPolicy.check("Perez-2031x", account))
                .contains(PasswordPolicy.CONTAINS_PERSONAL_DATA);
        assertThat(PasswordPolicy.check("jperez7781!", account))
                .contains(PasswordPolicy.CONTAINS_PERSONAL_DATA);
        assertThat(PasswordPolicy.check("ESQUINA-norte-9", account))
                .contains(PasswordPolicy.CONTAINS_PERSONAL_DATA);
        // "juan" tiene 4 letras y se revisa; "la" es demasiado corta y no.
        assertThat(PasswordPolicy.check("JuanTorre-55", account))
                .contains(PasswordPolicy.CONTAINS_PERSONAL_DATA);
        assertThat(PasswordPolicy.check("Mantel-Azul-47", account)).isEmpty();
    }

    @Test
    void ignoresMissingPersonalData() {
        assertThat(PasswordPolicy.check("Mantel-Azul-47", Arrays.asList(null, "", "mx")))
                .isEmpty();
    }

    @Test
    @DisplayName("los mensajes nunca repiten la contrasena")
    void messagesDoNotEchoThePassword() {
        assertThat(PasswordPolicy.check("Password2024!", NONE))
                .hasValueSatisfying(message -> assertThat(message).doesNotContain("Password2024"));
    }
}
