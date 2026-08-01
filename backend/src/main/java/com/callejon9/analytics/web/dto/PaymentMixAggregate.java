package com.callejon9.analytics.web.dto;

import com.callejon9.sale.domain.PaymentMethod;
import java.math.BigDecimal;

/** Conteo e ingreso por metodo de pago en el rango, ya sumado en SQL. */
public record PaymentMixAggregate(PaymentMethod method, long count, BigDecimal total) {
}
