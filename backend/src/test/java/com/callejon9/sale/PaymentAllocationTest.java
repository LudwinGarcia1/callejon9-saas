package com.callejon9.sale;

import com.callejon9.sale.domain.PaymentMethod;
import com.callejon9.sale.service.*;
import com.callejon9.sale.web.dto.PaymentRequest;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class PaymentAllocationTest {
    @Test void cashChangeIsIndependentOfInputOrderAndIncludesTip() {
        for (var inputs : List.of(
                List.of(new PaymentRequest(PaymentMethod.CASH, new BigDecimal("400")), new PaymentRequest(PaymentMethod.CARD, new BigDecimal("200"))),
                List.of(new PaymentRequest(PaymentMethod.CARD, new BigDecimal("200")), new PaymentRequest(PaymentMethod.CASH, new BigDecimal("400"))))) {
            var result = PaymentAllocation.allocate(inputs, new BigDecimal("550.00"));
            assertThat(result.stream().map(p -> p.amount()).reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("550");
            assertThat(result.stream().map(p -> p.change()).reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("50");
        }
    }
    @Test void rejectsBadMoneyAndNoncashExcessEvenWhenCashExists() {
        for (var value : List.of("-1", "0", "1.001", "100000000")) {
            assertThatThrownBy(() -> PaymentAllocation.allocate(List.of(new PaymentRequest(PaymentMethod.CASH, new BigDecimal(value))), new BigDecimal("500")))
                    .isInstanceOf(InvalidPaymentException.class);
        }
        assertThatThrownBy(() -> PaymentAllocation.allocate(List.of(new PaymentRequest(PaymentMethod.CARD, new BigDecimal("501")),
                new PaymentRequest(PaymentMethod.CASH, new BigDecimal("1"))), new BigDecimal("500"))).isInstanceOf(InvalidPaymentException.class);
    }
    @Test void multipleCashPayersReceiveOnlyCashChange() {
        var result = PaymentAllocation.allocate(List.of(new PaymentRequest(PaymentMethod.CASH, new BigDecimal("300")),
                new PaymentRequest(PaymentMethod.CASH, new BigDecimal("300"))), new BigDecimal("500"));
        assertThat(result.get(0).amount()).isEqualByComparingTo("300");
        assertThat(result.get(1).amount()).isEqualByComparingTo("200");
        assertThat(result.get(1).change()).isEqualByComparingTo("100");
    }
}
