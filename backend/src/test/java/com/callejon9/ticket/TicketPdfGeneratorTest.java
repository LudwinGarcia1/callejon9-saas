package com.callejon9.ticket;

import com.callejon9.sale.domain.PaymentMethod;
import com.callejon9.ticket.domain.Ticket;
import com.callejon9.ticket.domain.TicketItemSnapshot;
import com.callejon9.ticket.domain.TicketPaymentSnapshot;
import com.callejon9.ticket.service.TicketPdfGenerator;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("UT · TicketPdfGenerator")
class TicketPdfGeneratorTest {

    @Test
    @DisplayName("El recibo contiene pagos aplicados mixtos, efectivo recibido y cambio")
    void receiptContainsAppliedPaymentsReceivedCashAndChange() throws Exception {
        var ticket = Ticket.builder()
                .folio("TCK-TEST")
                .closedAt(Instant.now())
                .itemsSnapshot(List.of())
                .subtotal(new BigDecimal("500.00"))
                .tip(BigDecimal.ZERO)
                .total(new BigDecimal("500.00"))
                .paymentMethod(PaymentMethod.MIXED)
                .change(new BigDecimal("100.00"))
                .paymentsSnapshot(List.of(
                        new TicketPaymentSnapshot(PaymentMethod.CASH, new BigDecimal("300.00"),
                                new BigDecimal("400.00"), new BigDecimal("100.00")),
                        new TicketPaymentSnapshot(PaymentMethod.CARD, new BigDecimal("200.00"),
                                new BigDecimal("200.00"), BigDecimal.ZERO)))
                .build();

        try (var reader = new PdfReader(new TicketPdfGenerator().generate(ticket, "Restaurante"))) {
            var text = new PdfTextExtractor(reader).getTextFromPage(1);
            assertThat(text).contains("Efectivo: $300.00", "Recibido: $400.00",
                    "Tarjeta: $200.00", "Cambio: $100.00", "Total: $500.00");
        }
    }

    @Test
    @DisplayName("El recibo incluye desglose completo de partidas, propina voluntaria y totales correctos (CAL-129)")
    void receiptContainsItemsTipAndGrandTotal() throws Exception {
        var items = List.of(
                new TicketItemSnapshot(UUID.randomUUID(), "Hamburguesa Clásica", new BigDecimal("120.00"), 2, new BigDecimal("240.00")),
                new TicketItemSnapshot(UUID.randomUUID(), "Cerveza Artesanal", new BigDecimal("65.00"), 2, new BigDecimal("130.00"))
        );

        var payments = List.of(
                new TicketPaymentSnapshot(PaymentMethod.CARD, new BigDecimal("410.00"), new BigDecimal("410.00"), BigDecimal.ZERO)
        );

        var ticket = Ticket.builder()
                .folio("TCK-2026-VALERIA")
                .closedAt(Instant.now())
                .itemsSnapshot(items)
                .subtotal(new BigDecimal("370.00"))
                .tip(new BigDecimal("40.00"))
                .total(new BigDecimal("410.00"))
                .paymentMethod(PaymentMethod.CARD)
                .change(BigDecimal.ZERO)
                .paymentsSnapshot(payments)
                .build();

        try (var reader = new PdfReader(new TicketPdfGenerator().generate(ticket, "RestoHub Gourmet"))) {
            var text = new PdfTextExtractor(reader).getTextFromPage(1);
            assertThat(text)
                    .contains("RestoHub Gourmet")
                    .contains("TCK-2026-VALERIA")
                    .contains("Hamburguesa Clásica")
                    .contains("Cerveza Artesanal")
                    .contains("Subtotal: $370.00")
                    .contains("Propina: $40.00")
                    .contains("Total: $410.00");
        }
    }
}
