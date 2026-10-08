package com.callejon9.sale.service;

public class InvalidPaymentException extends RuntimeException {
    public InvalidPaymentException(String message) { super(message); }
}
