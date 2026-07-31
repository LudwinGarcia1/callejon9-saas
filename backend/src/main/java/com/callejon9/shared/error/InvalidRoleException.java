package com.callejon9.shared.error;

/** El rol solicitado no puede asignarse en este contexto. Se traduce a HTTP 400. */
public class InvalidRoleException extends RuntimeException {

    public InvalidRoleException(String message) {
        super(message);
    }
}
