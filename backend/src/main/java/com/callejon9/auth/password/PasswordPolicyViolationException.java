package com.callejon9.auth.password;

/** Una contrasena nueva no cumple la politica del producto. */
public class PasswordPolicyViolationException extends RuntimeException {

    public PasswordPolicyViolationException(String message) {
        super(message);
    }
}
