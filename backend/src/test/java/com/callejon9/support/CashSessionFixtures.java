package com.callejon9.support;

import com.callejon9.cashier.service.CashSessionService;
import com.callejon9.cashier.web.dto.OpenCashSessionRequest;
import com.callejon9.user.domain.User;
import java.math.BigDecimal;

/** Shared real-service setup for integration tests that perform checkout. */
public final class CashSessionFixtures {

    private CashSessionFixtures() {
    }

    public static void openFor(CashSessionService service, User cashier) {
        service.open(cashier.getId(), new OpenCashSessionRequest(BigDecimal.ZERO));
    }
}
