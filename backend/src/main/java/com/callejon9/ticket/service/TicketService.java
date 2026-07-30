package com.callejon9.ticket.service;

import com.callejon9.platform.tenant.domain.Tenant;
import com.callejon9.platform.tenant.repository.TenantRepository;
import com.callejon9.shared.error.ResourceNotFoundException;
import com.callejon9.tenancy.TenantContext;
import com.callejon9.ticket.domain.Ticket;
import com.callejon9.ticket.repository.TicketRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TicketService {

    private final TicketRepository ticketRepository;
    private final TenantRepository tenantRepository;
    private final TicketPdfGenerator pdfGenerator;

    public TicketService(
            TicketRepository ticketRepository,
            TenantRepository tenantRepository,
            TicketPdfGenerator pdfGenerator) {
        this.ticketRepository = ticketRepository;
        this.tenantRepository = tenantRepository;
        this.pdfGenerator = pdfGenerator;
    }

    @Transactional(readOnly = true)
    public Ticket getTicket(UUID id) {
        return ticketRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("El ticket " + id + " no existe."));
    }

    @Transactional(readOnly = true)
    public byte[] generatePdf(UUID id) {
        Ticket ticket = getTicket(id);
        String restaurantName = tenantRepository.findById(TenantContext.require())
                .map(Tenant::getName)
                .orElse("");
        return pdfGenerator.generate(ticket, restaurantName);
    }
}
