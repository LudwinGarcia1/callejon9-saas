package com.callejon9.analytics.web.dto;

import com.callejon9.sale.domain.PaymentMethod;
import java.math.BigDecimal;

/** Ventas del rango agrupadas por metodo de pago. {@code share} es porcentaje 0-100. */
public record PaymentMixRow(PaymentMethod method, long count, BigDecimal total, BigDecimal share) {
}
