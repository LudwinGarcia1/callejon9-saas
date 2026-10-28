package com.callejon9.sale.repository;

import com.callejon9.sale.domain.Payment;
import com.callejon9.cashier.web.dto.CashPaymentMethodSummary;
import com.callejon9.cashier.web.dto.CashPaymentTotals;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    @Query("""
            select new com.callejon9.cashier.web.dto.CashPaymentTotals(
                coalesce(sum(payment.receivedAmount), 0),
                coalesce(sum(payment.receivedAmount - payment.amount), 0))
            from Payment payment
            join Sale sale on sale.id = payment.saleId
            where sale.cashSessionId = :cashSessionId
              and payment.method = com.callejon9.sale.domain.PaymentMethod.CASH
              and payment.status = 'COMPLETED'
            """)
    CashPaymentTotals cashTotalsForSession(@Param("cashSessionId") UUID cashSessionId);

    @Query("""
            select new com.callejon9.cashier.web.dto.CashPaymentMethodSummary(
                payment.method, count(payment), coalesce(sum(payment.amount), 0),
                coalesce(sum(payment.receivedAmount), 0),
                coalesce(sum(payment.receivedAmount - payment.amount), 0))
            from Payment payment
            join Sale sale on sale.id = payment.saleId
            where sale.cashSessionId = :cashSessionId and payment.status = 'COMPLETED'
            group by payment.method
            order by payment.method
            """)
    List<CashPaymentMethodSummary> paymentSummaryForSession(
            @Param("cashSessionId") UUID cashSessionId);
}
