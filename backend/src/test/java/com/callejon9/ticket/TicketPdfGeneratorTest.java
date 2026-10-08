package com.callejon9.ticket;

import com.callejon9.sale.domain.PaymentMethod;
import com.callejon9.ticket.domain.Ticket;
import com.callejon9.ticket.domain.TicketPaymentSnapshot;
import com.callejon9.ticket.service.TicketPdfGenerator;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class TicketPdfGeneratorTest {
    @Test
    void receiptContainsAppliedPaymentsReceivedCashAndChange() throws Exception {
        var ticket = Ticket.builder().folio("TCK-TEST").closedAt(Instant.now())
                .itemsSnapshot(List.of()).subtotal(new BigDecimal("500.00"))
                .tip(BigDecimal.ZERO).total(new BigDecimal("500.00"))
                .paymentMethod(PaymentMethod.MIXED).change(new BigDecimal("100.00"))
                .paymentsSnapshot(List.of(
                        new TicketPaymentSnapshot(PaymentMethod.CASH, new BigDecimal("300.00"),
                                new BigDecimal("400.00"), new BigDecimal("100.00")),
                        new TicketPaymentSnapshot(PaymentMethod.CARD, new BigDecimal("200.00"),
                                new BigDecimal("200.00"), BigDecimal.ZERO))).build();
        try (var reader = new PdfReader(new TicketPdfGenerator().generate(ticket, "Restaurante"))) {
            var text = new PdfTextExtractor(reader).getTextFromPage(1);
            assertThat(text).contains("Efectivo: $300.00", "Recibido: $400.00",
                    "Tarjeta: $200.00", "Cambio: $100.00", "Total: $500.00");
        }
    }
}
