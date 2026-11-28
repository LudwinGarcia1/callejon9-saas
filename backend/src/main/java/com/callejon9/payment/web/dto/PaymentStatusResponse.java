package com.callejon9.payment.web.dto;

import java.time.Instant;
import java.util.UUID;

public record PaymentStatusResponse(
        UUID orderId,
        String folio,
        String status,
        boolean paid,
        String ticketFolio,
        Instant closedAt
) {}
