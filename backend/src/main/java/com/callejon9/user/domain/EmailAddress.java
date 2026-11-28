package com.callejon9.user.domain;

import java.util.Locale;

/**
 * Forma canonica del correo de un usuario: sin espacios a los lados y en
 * minusculas (CAL-180). Se aplica al guardarlo y al buscarlo, para que
 * {@code Ana.Caja@...} y {@code ana.caja@...} sean el mismo usuario dentro de
 * un restaurante; la base lo respalda con el indice unico sobre
 * {@code lower(email)} de V16.
 */
public final class EmailAddress {

    private EmailAddress() {
    }

    public static String normalize(String email) {
        return email == null ? null : email.strip().toLowerCase(Locale.ROOT);
    }
}
