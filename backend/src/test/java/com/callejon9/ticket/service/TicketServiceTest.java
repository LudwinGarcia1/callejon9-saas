package com.callejon9.ticket.service;

import com.callejon9.platform.tenant.domain.Tenant;
import com.callejon9.platform.tenant.repository.TenantRepository;
import com.callejon9.shared.error.ResourceNotFoundException;
import com.callejon9.tenancy.TenantContext;
import com.callejon9.ticket.domain.Ticket;
import com.callejon9.ticket.repository.TicketRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("UT · TicketService")
class TicketServiceTest {

    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private TicketPdfGenerator pdfGenerator;

    private TicketService ticketService;

    @BeforeEach
    void setUp() {
        TenantContext.set(TENANT_ID);
        ticketService = new TicketService(ticketRepository, tenantRepository, pdfGenerator);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Nested
    @DisplayName("getTicket — Búsqueda por ID")
    class GetTicketTests {

        @Test
        @DisplayName("Retorna el ticket cuando existe")
        void returnsTicketWhenExists() {
            UUID ticketId = UUID.randomUUID();
            Ticket ticket = Ticket.builder()
                    .folio("TCK-2026-0001")
                    .closedAt(Instant.now())
                    .subtotal(BigDecimal.valueOf(250.00))
                    .total(BigDecimal.valueOf(250.00))
                    .build();
            ticket.setId(ticketId);
            ticket.setTenantId(TENANT_ID);

            when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));

            Ticket result = ticketService.getTicket(ticketId);

            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(ticketId);
            assertThat(result.getFolio()).isEqualTo("TCK-2026-0001");
            verify(ticketRepository).findById(ticketId);
        }

        @Test
        @DisplayName("Lanza ResourceNotFoundException si el ticket no existe")
        void throwsNotFoundWhenTicketDoesNotExist() {
            UUID ticketId = UUID.randomUUID();
            when(ticketRepository.findById(ticketId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> ticketService.getTicket(ticketId))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("no existe");
        }
    }

    @Nested
    @DisplayName("getTicketByFolio — Búsqueda por Folio")
    class GetTicketByFolioTests {

        @Test
        @DisplayName("Retorna el ticket cuando el folio existe")
        void returnsTicketWhenFolioExists() {
            String folio = "TCK-2026-1020";
            UUID ticketId = UUID.randomUUID();
            Ticket ticket = Ticket.builder()
                    .folio(folio)
                    .closedAt(Instant.now())
                    .subtotal(BigDecimal.valueOf(180.00))
                    .total(BigDecimal.valueOf(180.00))
                    .build();
            ticket.setId(ticketId);
            ticket.setTenantId(TENANT_ID);

            when(ticketRepository.findByFolio(folio)).thenReturn(Optional.of(ticket));

            Ticket result = ticketService.getTicketByFolio(folio);

            assertThat(result).isNotNull();
            assertThat(result.getFolio()).isEqualTo(folio);
            verify(ticketRepository).findByFolio(folio);
        }

        @Test
        @DisplayName("Lanza ResourceNotFoundException si el folio no existe o es de otro restaurante (RLS)")
        void throwsNotFoundWhenFolioDoesNotExist() {
            String folio = "TCK-UNKNOWN-999";
            when(ticketRepository.findByFolio(folio)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> ticketService.getTicketByFolio(folio))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining(folio);
        }
    }

    @Nested
    @DisplayName("generatePdf — Generación de PDF de Ticket")
    class GeneratePdfTests {

        @Test
        @DisplayName("Genera PDF exitosamente asociando el nombre comercial del restaurante")
        void generatesPdfWithRestaurantName() {
            UUID ticketId = UUID.randomUUID();
            Ticket ticket = Ticket.builder()
                    .folio("TCK-2026-0002")
                    .closedAt(Instant.now())
                    .subtotal(BigDecimal.valueOf(500.00))
                    .total(BigDecimal.valueOf(500.00))
                    .build();
            ticket.setId(ticketId);
            ticket.setTenantId(TENANT_ID);

            Tenant tenant = Tenant.builder()
                    .name("Restaurante Callejón 9")
                    .slug("callejon9")
                    .active(true)
                    .build();
            tenant.setId(TENANT_ID);

            byte[] expectedBytes = "%PDF-1.4 dummy".getBytes();

            when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
            when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
            when(pdfGenerator.generate(ticket, "Restaurante Callejón 9")).thenReturn(expectedBytes);

            byte[] pdfResult = ticketService.generatePdf(ticketId);

            assertThat(pdfResult).isEqualTo(expectedBytes);
            verify(pdfGenerator).generate(ticket, "Restaurante Callejón 9");
        }

        @Test
        @DisplayName("Genera PDF con nombre vacío si el inquilino no tiene nombre configurado")
        void generatesPdfWithEmptyNameWhenTenantNotFound() {
            UUID ticketId = UUID.randomUUID();
            Ticket ticket = Ticket.builder()
                    .folio("TCK-2026-0003")
                    .closedAt(Instant.now())
                    .subtotal(BigDecimal.valueOf(300.00))
                    .total(BigDecimal.valueOf(300.00))
                    .build();
            ticket.setId(ticketId);
            ticket.setTenantId(TENANT_ID);

            when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
            when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.empty());
            when(pdfGenerator.generate(ticket, "")).thenReturn(new byte[]{1, 2, 3});

            byte[] pdfResult = ticketService.generatePdf(ticketId);

            assertThat(pdfResult).isNotEmpty();
            verify(pdfGenerator).generate(ticket, "");
        }
    }
}
