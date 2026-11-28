package com.callejon9.payment.web.dto;

import java.math.BigDecimal;

public record PreferenceResponse(
        String preferenceId,
        String initPoint,
        String sandboxInitPoint,
        String qrCode,
        String externalReference,
        BigDecimal total,
        String folio,
        boolean isMock
) {}
