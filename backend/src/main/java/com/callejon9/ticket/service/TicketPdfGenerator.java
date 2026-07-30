package com.callejon9.ticket.service;

import com.callejon9.ticket.domain.Ticket;
import com.callejon9.ticket.domain.TicketItemSnapshot;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import java.io.ByteArrayOutputStream;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Component;

/**
 * Genera el PDF del ticket con OpenPDF: un recibo sencillo, sin diseno
 * elaborado, con lo minimo que un cliente esperaria ver al pagar la cuenta.
 */
@Component
public class TicketPdfGenerator {

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm 'UTC'").withZone(ZoneOffset.UTC);

    public byte[] generate(Ticket ticket, String restaurantName) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Document document = new Document();
            PdfWriter.getInstance(document, out);
            document.open();

            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16);
            Font normalFont = FontFactory.getFont(FontFactory.HELVETICA, 10);
            Font boldFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);

            document.add(new Paragraph(restaurantName, titleFont));
            document.add(new Paragraph("Folio: " + ticket.getFolio(), normalFont));
            document.add(new Paragraph("Fecha: " + DATE_FORMAT.format(ticket.getClosedAt()), normalFont));
            document.add(new Paragraph(" "));

            document.add(buildItemsTable(ticket, normalFont, boldFont));

            document.add(new Paragraph(" "));
            document.add(new Paragraph("Subtotal: $" + ticket.getSubtotal().toPlainString(), normalFont));
            document.add(new Paragraph("Propina: $" + ticket.getTip().toPlainString(), normalFont));
            document.add(new Paragraph("Total: $" + ticket.getTotal().toPlainString(), boldFont));
            document.add(new Paragraph(
                    "Metodo de pago: " + paymentMethodLabel(ticket), normalFont));

            document.close();
            return out.toByteArray();
        } catch (Exception generationFailure) {
            throw new IllegalStateException(
                    "No se pudo generar el PDF del ticket " + ticket.getFolio() + ".", generationFailure);
        }
    }

    private PdfPTable buildItemsTable(Ticket ticket, Font normalFont, Font boldFont) {
        PdfPTable table = new PdfPTable(4);
        table.setWidthPercentage(100);

        addHeaderCell(table, "Producto", boldFont);
        addHeaderCell(table, "Cant.", boldFont);
        addHeaderCell(table, "Precio", boldFont);
        addHeaderCell(table, "Subtotal", boldFont);

        for (TicketItemSnapshot item : ticket.getItemsSnapshot()) {
            table.addCell(new PdfPCell(new Paragraph(item.productName(), normalFont)));
            table.addCell(new PdfPCell(new Paragraph(String.valueOf(item.quantity()), normalFont)));
            table.addCell(new PdfPCell(new Paragraph(item.unitPrice().toPlainString(), normalFont)));
            table.addCell(new PdfPCell(new Paragraph(item.subtotal().toPlainString(), normalFont)));
        }
        return table;
    }

    private void addHeaderCell(PdfPTable table, String text, Font font) {
        PdfPCell cell = new PdfPCell(new Paragraph(text, font));
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        table.addCell(cell);
    }

    private String paymentMethodLabel(Ticket ticket) {
        return switch (ticket.getPaymentMethod()) {
            case CASH -> "Efectivo";
            case CARD -> "Tarjeta";
            case TRANSFER -> "Transferencia";
            case MIXED -> "Mixto";
            case MERCADOPAGO -> "MercadoPago";
        };
    }
}
