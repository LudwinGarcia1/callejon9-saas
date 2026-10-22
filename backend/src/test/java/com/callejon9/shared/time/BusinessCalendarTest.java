package com.callejon9.shared.time;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("UT · BusinessCalendar")
class BusinessCalendarTest {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("America/Mexico_City");
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
            LocalDate businessDay = LocalDate.of(2026, 10, 20);
            Instant dinnerTime = businessDay.atTime(19, 0).atZone(BUSINESS_ZONE).toInstant();
            BusinessCalendar atDinnerTime = new BusinessCalendar(
                    BUSINESS_ZONE, Clock.fixed(dinnerTime, ZoneOffset.UTC));

            assertThat(dinnerTime.atZone(ZoneOffset.UTC).toLocalDate()).isEqualTo(businessDay.plusDays(1));
            assertThat(atDinnerTime.today()).isEqualTo(businessDay);
        }

        @Test
        @DisplayName("toInstantRange genera un rango cerrado por inicio y abierto por fin exactamente en medianoche local")
        void toInstantRangeSpansExactLocalMidnightBoundaries() {
            LocalDate from = LocalDate.of(2026, 10, 20);
            LocalDate to = LocalDate.of(2026, 10, 20);

            InstantRange range = calendar.toInstantRange(from, to);

            Instant expectedStart = from.atStartOfDay(BUSINESS_ZONE).toInstant();
            Instant expectedEnd = to.plusDays(1).atStartOfDay(BUSINESS_ZONE).toInstant();
            Instant dinnerTime = from.atTime(19, 0).atZone(BUSINESS_ZONE).toInstant();

            assertThat(range.start()).isEqualTo(expectedStart);
            assertThat(range.endExclusive()).isEqualTo(expectedEnd);
            assertThat(dinnerTime.isAfter(range.start())).isTrue();
            assertThat(dinnerTime.isBefore(range.endExclusive())).isTrue();
        }
    }
}
