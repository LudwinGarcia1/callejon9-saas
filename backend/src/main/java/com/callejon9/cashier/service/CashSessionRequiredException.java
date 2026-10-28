package com.callejon9.cashier.service;

public class CashSessionRequiredException extends RuntimeException {

    public CashSessionRequiredException() {
        super("Abre un turno de caja antes de cobrar una cuenta.");
    }
}
