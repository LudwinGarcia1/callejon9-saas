package com.callejon9.payment.web.dto;

public record SyncPaymentRequest(
        String paymentId,
        String externalReference
) {}
