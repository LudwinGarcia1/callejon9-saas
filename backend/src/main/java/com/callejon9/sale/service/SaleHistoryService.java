package com.callejon9.sale.service;

import com.callejon9.sale.repository.SaleRepository;
import com.callejon9.sale.web.dto.SalesHistoryResponse;
import com.callejon9.shared.time.BusinessCalendar;
import com.callejon9.shared.time.InstantRange;
import com.callejon9.shared.web.PageResponse;
import java.time.LocalDate;
import org.springframework.data.domain.Pageable;
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
    private final com.callejon9.analytics.repository.AnalyticsRepository analyticsRepository;

    public SaleHistoryService(SaleRepository saleRepository, BusinessCalendar businessCalendar,
            com.callejon9.analytics.repository.AnalyticsRepository analyticsRepository) {
        this.saleRepository = saleRepository;
        this.businessCalendar = businessCalendar;
        this.analyticsRepository = analyticsRepository;
    }

    /**
     * Las filas llegan paginadas (CAL-16); el resumen y la mezcla de pago
     * cubren el rango completo, no solo la pagina, porque son los totales que
     * el cajero concilia.
     */
    @Transactional(readOnly = true)
    public SalesHistoryResponse getHistory(LocalDate from, LocalDate to, Pageable pageable) {
        LocalDate today = businessCalendar.today();
        LocalDate effectiveFrom = from != null ? from : (to != null ? to : today);
        LocalDate effectiveTo = to != null ? to : effectiveFrom;

        InstantRange range = businessCalendar.toInstantRange(effectiveFrom, effectiveTo);

        return new SalesHistoryResponse(
                PageResponse.from(saleRepository.findHistory(range.start(), range.endExclusive(), pageable)),
                saleRepository.summarize(range.start(), range.endExclusive()),
                analyticsRepository.paymentMix(range.start(), range.endExclusive()));
    }
}
