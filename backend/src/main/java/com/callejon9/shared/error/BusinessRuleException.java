package com.callejon9.shared.error;

/** Una regla de negocio impide completar la operacion. Se traduce a HTTP 409. */
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
