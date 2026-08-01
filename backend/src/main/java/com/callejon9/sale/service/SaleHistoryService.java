package com.callejon9.sale.service;

import com.callejon9.sale.repository.SaleRepository;
import com.callejon9.sale.web.dto.SalesHistoryResponse;
import com.callejon9.shared.time.BusinessCalendar;
import com.callejon9.shared.time.InstantRange;
import java.time.LocalDate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consulta el historial de ventas ya cobradas.
 *
 * El rango se resuelve en la zona horaria del negocio, no en UTC (ver
 * {@link BusinessCalendar}). La diferencia no es cosmetica: con Mexico City en
 * -06:00, resolver el dia en UTC hace que a partir de las 18:00 locales el
 * historial se vacie, porque UTC ya paso al dia siguiente. Se rompe justo
 * durante el servicio de la noche, que es cuando un cajero mira las ventas del
 * dia. Y en un rango explicito era peor: mostraba la cena de la noche ANTERIOR
 * y escondia la de la noche en curso.
 *
 * Las columnas son timestamptz, asi que la base guardaba instantes correctos.
 * Lo unico equivocado era que instantes delimitan "un dia".
 *
 * Reglas cuando falta alguno de los dos parametros: sin ninguno, hoy; con uno
 * solo, ese mismo dia.
 */
@Service
public class SaleHistoryService {

    private final SaleRepository saleRepository;
    private final BusinessCalendar businessCalendar;

    public SaleHistoryService(SaleRepository saleRepository, BusinessCalendar businessCalendar) {
        this.saleRepository = saleRepository;
        this.businessCalendar = businessCalendar;
    }

    @Transactional(readOnly = true)
    public SalesHistoryResponse getHistory(LocalDate from, LocalDate to) {
        LocalDate today = businessCalendar.today();
        LocalDate effectiveFrom = from != null ? from : (to != null ? to : today);
        LocalDate effectiveTo = to != null ? to : effectiveFrom;

        InstantRange range = businessCalendar.toInstantRange(effectiveFrom, effectiveTo);

        return new SalesHistoryResponse(
                saleRepository.findHistory(range.start(), range.endExclusive()),
                saleRepository.summarize(range.start(), range.endExclusive()));
    }
}
