package com.callejon9.ticket.domain;

import com.callejon9.sale.domain.PaymentMethod;
import java.math.BigDecimal;

public record TicketPaymentSnapshot(PaymentMethod method, BigDecimal amount,
        BigDecimal receivedAmount, BigDecimal change) { }
