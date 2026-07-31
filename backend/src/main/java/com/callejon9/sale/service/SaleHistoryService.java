package com.callejon9.sale.service;

import com.callejon9.sale.repository.SaleRepository;
import com.callejon9.sale.web.dto.SalesHistoryResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consulta el historial de ventas ya cobradas. El rango de fechas se resuelve
 * en UTC, la misma zona que usa el resto del sistema para folios y timestamps
 * de ticket (no hay zona horaria por tenant hoy).
 *
 * Reglas para resolver el rango cuando falta alguno de los dos parametros:
 * sin ninguno, hoy; solo uno de los dos, ese mismo dia (rango de un solo dia).
 */
@Service
public class SaleHistoryService {

    private final SaleRepository saleRepository;

    public SaleHistoryService(SaleRepository saleRepository) {
        this.saleRepository = saleRepository;
    }

    @Transactional(readOnly = true)
    public SalesHistoryResponse getHistory(LocalDate from, LocalDate to) {
        LocalDate effectiveFrom = from != null ? from : (to != null ? to : LocalDate.now(ZoneOffset.UTC));
        LocalDate effectiveTo = to != null ? to : effectiveFrom;

        Instant rangeStart = effectiveFrom.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant rangeEndExclusive = effectiveTo.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        return new SalesHistoryResponse(
                saleRepository.findHistory(rangeStart, rangeEndExclusive),
                saleRepository.summarize(rangeStart, rangeEndExclusive));
    }
}
