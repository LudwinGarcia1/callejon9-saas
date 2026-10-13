package com.callejon9.sale.service;

import com.callejon9.sale.domain.PaymentMethod;
import com.callejon9.sale.web.dto.PaymentRequest;
import com.callejon9.ticket.domain.TicketPaymentSnapshot;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/** Montos recibidos y aplicados en centavos; solo el efectivo admite cambio. */
public final class PaymentAllocation {
    private PaymentAllocation() { }
    public static List<TicketPaymentSnapshot> allocate(List<PaymentRequest> requests, BigDecimal total) {
        if (requests == null || requests.isEmpty() || requests.size() > 50) {
            throw new InvalidPaymentException("Registra entre uno y 50 pagos.");
        }
        BigDecimal cash = BigDecimal.ZERO, nonCash = BigDecimal.ZERO;
        for (var payment : requests) {
            if (payment == null || payment.method() == null || payment.method() == PaymentMethod.MIXED
                    || payment.amount() == null || payment.amount().signum() <= 0
                    || payment.amount().stripTrailingZeros().scale() > 2
                    || payment.amount().compareTo(new BigDecimal("99999999.99")) > 0) {
                throw new InvalidPaymentException("Cada pago requiere un método concreto y un monto positivo con hasta dos decimales.");
            }
            if (payment.method() == PaymentMethod.CASH) cash = cash.add(payment.amount());
            else nonCash = nonCash.add(payment.amount());
        }
        if (nonCash.compareTo(total) > 0) {
            throw new InvalidPaymentException("Los pagos sin efectivo exceden el total.");
        }
        if (cash.add(nonCash).compareTo(total) < 0) {
            throw new InvalidPaymentException("Los pagos no cubren el total de la cuenta.");
        }
        BigDecimal remainingCash = total.subtract(nonCash);
        var result = new ArrayList<TicketPaymentSnapshot>();
        for (var payment : requests) {
            BigDecimal received = payment.amount().setScale(2, RoundingMode.UNNECESSARY);
            BigDecimal applied = payment.method() == PaymentMethod.CASH ? received.min(remainingCash) : received;
            if (payment.method() == PaymentMethod.CASH) remainingCash = remainingCash.subtract(applied);
            result.add(new TicketPaymentSnapshot(payment.method(), applied, received, received.subtract(applied)));
        }
        return List.copyOf(result);
    }
}
