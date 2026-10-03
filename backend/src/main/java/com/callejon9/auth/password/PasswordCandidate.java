package com.callejon9.auth.password;

import java.util.List;

/**
 * Peticion que fija una contrasena nueva. Expone, ademas de la contrasena,
 * los datos de la cuenta que esta no debe contener.
 */
public interface PasswordCandidate {

    String password();

    List<String> personalData();
}
