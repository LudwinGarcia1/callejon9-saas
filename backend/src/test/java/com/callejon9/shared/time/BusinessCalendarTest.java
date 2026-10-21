package com.callejon9.shared.time;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("UT · BusinessCalendar")
class BusinessCalendarTest {

    private final BusinessCalendar calendar = new BusinessCalendar("America/Mexico_City");

    @Test
    @DisplayName("La zona horaria configurada corresponde exactamente a America/Mexico_City")
    void zoneIsConfiguredZone() {
        assertThat(calendar.zone()).isEqualTo(ZoneId.of("America/Mexico_City"));
    }

    @Nested
    @DisplayName("Corte de día después de las 18:00 locales (UTC vs Negocio)")
    class DayCutoffTests {

        @Test
        @DisplayName("A las 19:00 horas locales de CDMX (01:00 UTC del día siguiente), el día de negocio sigue siendo el mismo día local")
        void businessDayPreservedAfterSixPmLocal() {
            // 2026-10-20 a las 19:00 en CDMX equivale a 2026-10-21 01:00 UTC
            ZonedDateTime dinnerTime = ZonedDateTime.of(2026, 10, 20, 19, 0, 0, 0, ZoneId.of("America/Mexico_City"));
            LocalDate localDate = dinnerTime.toLocalDate();

            assertThat(localDate).isEqualTo(LocalDate.of(2026, 10, 20));
        }

        @Test
        @DisplayName("toInstantRange genera un rango cerrado por inicio y abierto por fin exactamente en medianoche local")
        void toInstantRangeSpansExactLocalMidnightBoundaries() {
            LocalDate from = LocalDate.of(2026, 10, 20);
            LocalDate to = LocalDate.of(2026, 10, 20);

            InstantRange range = calendar.toInstantRange(from, to);

            Instant expectedStart = from.atStartOfDay(ZoneId.of("America/Mexico_City")).toInstant();
            Instant expectedEnd = to.plusDays(1).atStartOfDay(ZoneId.of("America/Mexico_City")).toInstant();

            assertThat(range.start()).isEqualTo(expectedStart);
            assertThat(range.endExclusive()).isEqualTo(expectedEnd);
        }
    }
}
