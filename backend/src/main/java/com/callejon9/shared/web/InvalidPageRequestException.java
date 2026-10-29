package com.callejon9.shared.web;

/** {@code page}, {@code size} o {@code sort} fuera de lo permitido. Se traduce a HTTP 400. */
public class InvalidPageRequestException extends RuntimeException {

    public InvalidPageRequestException(String message) {
        super(message);
    }
}
