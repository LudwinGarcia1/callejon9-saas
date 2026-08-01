package com.callejon9.shared.time;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Resuelve "hoy" y los limites de un rango de dias en la zona horaria del
 * negocio, no en UTC.
 *
 * Con Mexico City en -06:00, resolver un dia en UTC lo corta a la mitad: a
 * partir de las 18:00 locales UTC ya paso al dia siguiente. Cualquier
 * agregacion "por dia" (historial de ventas, reportes) que ignore esto
 * termina partiendo un servicio de cena en dos filas o mostrando la cena
 * equivocada. Ver SaleHistoryService para el caso que origino esta clase.
 */
@Component
public class BusinessCalendar {

    private final ZoneId zone;

    public BusinessCalendar(@Value("${app.timezone}") String businessTimezone) {
        this.zone = ZoneId.of(businessTimezone);
    }

    public ZoneId zone() {
        return zone;
    }

    public LocalDate today() {
        return LocalDate.now(zone);
    }

    /**
     * Convierte un rango de dias de calendario [from, to] (ambos inclusive, en
     * la zona del negocio) al rango de instantes [start, endExclusive) que
     * corresponde a esos mismos dias.
     *
     * atStartOfDay(ZoneId) resuelve el horario de verano por su cuenta. Con un
     * ZoneOffset fijo el rango quedaria corrido una hora la noche en que el
     * pais cambia la hora.
     */
    public InstantRange toInstantRange(LocalDate from, LocalDate to) {
        Instant start = from.atStartOfDay(zone).toInstant();
        Instant endExclusive = to.plusDays(1).atStartOfDay(zone).toInstant();
        return new InstantRange(start, endExclusive);
    }
}
