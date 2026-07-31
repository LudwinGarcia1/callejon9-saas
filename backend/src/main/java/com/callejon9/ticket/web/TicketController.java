package com.callejon9.ticket.web;

import com.callejon9.ticket.service.TicketService;
import com.callejon9.ticket.web.dto.TicketResponse;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Cualquier usuario autenticado puede consultar un ticket (ver SecurityConfig: anyRequest().authenticated()). */
@RestController
@RequestMapping("/api/v1/tickets")
public class TicketController {

    private final TicketService ticketService;

    public TicketController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @GetMapping("/{id}")
    public TicketResponse get(@PathVariable UUID id) {
        return TicketResponse.from(ticketService.getTicket(id));
    }

    /** Para el historial de ventas: localizar un ticket cuando solo se tiene su folio a mano. */
    @GetMapping(params = "folio")
    public TicketResponse getByFolio(@RequestParam String folio) {
        return TicketResponse.from(ticketService.getTicketByFolio(folio));
    }

    @GetMapping(value = "/{id}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> pdf(@PathVariable UUID id) {
        byte[] pdf = ticketService.generatePdf(id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }
}
