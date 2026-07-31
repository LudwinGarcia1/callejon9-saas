package com.callejon9.ticket.repository;

import com.callejon9.ticket.domain.Ticket;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * No filtra por tenant en las consultas: las politicas RLS de PostgreSQL ya
 * limitan las filas visibles al tenant activo.
 */
public interface TicketRepository extends JpaRepository<Ticket, UUID> {

    Optional<Ticket> findByFolio(String folio);
}
