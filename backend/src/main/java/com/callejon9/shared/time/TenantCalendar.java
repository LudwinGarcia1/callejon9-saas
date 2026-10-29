package com.callejon9.shared.time;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Calendario de un restaurante ya resuelto: su zona y el reloj. Se obtiene
 * con {@link BusinessCalendar#current()}.
 */
public record TenantCalendar(ZoneId zone, Clock clock) {

    public LocalDate today() {
        return LocalDate.now(clock.withZone(zone));
    }

    /**
     * Convierte un rango de dias de calendario [from, to] (ambos inclusive, en
     * la zona del restaurante) al rango de instantes [start, endExclusive) que
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
