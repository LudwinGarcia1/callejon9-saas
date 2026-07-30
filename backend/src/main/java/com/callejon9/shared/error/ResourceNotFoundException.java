package com.callejon9.shared.error;

/** El recurso solicitado no existe en el tenant activo. Se traduce a HTTP 404. */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
