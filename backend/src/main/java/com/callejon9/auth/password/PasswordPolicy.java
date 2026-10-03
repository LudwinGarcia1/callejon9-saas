package com.callejon9.auth.password;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Politica de contrasenas para toda contrasena nueva (alta de restaurante y
 * alta de usuario). El login no la aplica: exigirla ahi revelaria la politica
 * y dejaria fuera a cuentas creadas antes de que existiera.
 *
 * <p>Las reglas se evaluan en orden y se informa solo la primera que falla.
 * Los mensajes son fijos y nunca repiten la contrasena ni el dato personal
 * que coincidio.
 *
 * <ol>
 *   <li>Minimo {@value #MIN_LENGTH} caracteres.</li>
 *   <li>Maximo {@value #MAX_BYTES} bytes en UTF-8: bcrypt ignora en silencio
 *       todo lo que excede ese limite, asi que una contrasena mas larga
 *       aparentaria una fortaleza que no tiene.</li>
 *   <li>Al menos una letra y un numero.</li>
 *   <li>Al menos {@value #MIN_DISTINCT_CHARS} caracteres distintos, para
 *       descartar cadenas como {@code aaaaaaaaa1}.</li>
 *   <li>No figura en la lista de contrasenas comunes, ni tal cual ni sin los
 *       digitos y simbolos del final ({@code Password2024!} se reduce a
 *       {@code password}).</li>
 *   <li>No contiene datos de la propia cuenta: parte local del correo,
 *       identificador del restaurante, palabras del nombre.</li>
 * </ol>
 */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 10;
    public static final int MAX_BYTES = 72;
    public static final int MIN_DISTINCT_CHARS = 5;

    /** Un dato personal mas corto que esto genera falsos positivos ("ana", "mx"). */
    private static final int MIN_PERSONAL_TERM_LENGTH = 4;

    private static final String COMMON_PASSWORDS_RESOURCE = "/security/common-passwords.txt";

    static final String TOO_SHORT =
            "La contrasena debe tener al menos " + MIN_LENGTH + " caracteres.";
    static final String TOO_LONG =
            "La contrasena no puede exceder " + MAX_BYTES
                    + " caracteres (las letras acentuadas cuentan doble).";
    static final String MISSING_LETTER_OR_DIGIT =
            "La contrasena debe combinar letras y numeros.";
    static final String TOO_REPETITIVE =
            "La contrasena repite demasiado los mismos caracteres.";
    static final String TOO_COMMON =
            "Esa contrasena es demasiado comun. Elige otra.";
    static final String CONTAINS_PERSONAL_DATA =
            "La contrasena no debe incluir tu nombre, tu correo ni el identificador del restaurante.";

    private static final Set<String> COMMON_PASSWORDS = loadCommonPasswords();

    private PasswordPolicy() {
    }

    /**
     * Devuelve el mensaje de la primera regla incumplida, o vacio si la
     * contrasena es aceptable.
     *
     * @param personalData datos de la cuenta que la contrasena no debe
     *                     contener; se ignoran los nulos y los cortos.
     */
    public static Optional<String> check(String password, Collection<String> personalData) {
        if (password.codePointCount(0, password.length()) < MIN_LENGTH) {
            return Optional.of(TOO_SHORT);
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            return Optional.of(TOO_LONG);
        }
        if (password.codePoints().noneMatch(Character::isLetter)
                || password.codePoints().noneMatch(Character::isDigit)) {
            return Optional.of(MISSING_LETTER_OR_DIGIT);
        }
        if (password.codePoints().map(Character::toLowerCase).distinct().count()
                < MIN_DISTINCT_CHARS) {
            return Optional.of(TOO_REPETITIVE);
        }

        String normalized = normalize(password);
        if (COMMON_PASSWORDS.contains(normalized)
                || COMMON_PASSWORDS.contains(stripTrailingDigitsAndSymbols(normalized))) {
            return Optional.of(TOO_COMMON);
        }
        if (containsPersonalData(normalized, personalData)) {
            return Optional.of(CONTAINS_PERSONAL_DATA);
        }
        return Optional.empty();
    }

    private static boolean containsPersonalData(String normalizedPassword,
                                                Collection<String> personalData) {
        for (String value : personalData) {
            if (value == null) {
                continue;
            }
            String normalizedValue = normalize(value);
            int at = normalizedValue.indexOf('@');
            if (at >= 0) {
                normalizedValue = normalizedValue.substring(0, at);
            }
            // Un nombre completo o un slug con guiones se revisan por partes:
            // "Juan Perez" no debe permitir "perez2024!".
            for (String term : normalizedValue.split("[^\\p{L}\\p{N}]+")) {
                if (term.length() >= MIN_PERSONAL_TERM_LENGTH
                        && normalizedPassword.contains(term)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Minusculas y sin acentos, para que "Contraseña" y "contrasena" coincidan. */
    static String normalize(String value) {
        String decomposed = Normalizer.normalize(value, Normalizer.Form.NFD);
        return decomposed.replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }

    private static String stripTrailingDigitsAndSymbols(String value) {
        return value.replaceFirst("[^\\p{L}]+$", "");
    }

    private static Set<String> loadCommonPasswords() {
        InputStream stream = PasswordPolicy.class.getResourceAsStream(COMMON_PASSWORDS_RESOURCE);
        if (stream == null) {
            throw new IllegalStateException(
                    "No se encontro la lista de contrasenas comunes: " + COMMON_PASSWORDS_RESOURCE);
        }
        Set<String> passwords = new HashSet<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            reader.lines()
                    .map(String::strip)
                    .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                    .map(PasswordPolicy::normalize)
                    .forEach(passwords::add);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        return Set.copyOf(passwords);
    }
}
