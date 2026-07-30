package com.callejon9.auth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Los usuarios del sistema Flask deben poder entrar con su contrasena actual.
 * El hash de este test lo genero la libreria bcrypt de Python
 * (bcrypt.hashpw(b'Demo1234!', bcrypt.gensalt())).
 */
@DisplayName("Compatibilidad con los hashes bcrypt del sistema Flask")
class BcryptCompatibilityTest {

    private static final String PYTHON_HASH = "$2b$12$n7o.2GKM.xj7Xsmhjo/T4.uoaw/IrIrROZsSVTZODTMUiNHE0OSaS";

    private final PasswordEncoder encoder = new BCryptPasswordEncoder();

    @Test
    void springAcceptsAHashProducedByPythonBcrypt() {
        assertThat(encoder.matches("Demo1234!", PYTHON_HASH)).isTrue();
    }

    @Test
    void springRejectsTheWrongPassword() {
        assertThat(encoder.matches("contrasena-incorrecta", PYTHON_HASH)).isFalse();
    }
}
