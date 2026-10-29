package com.callejon9.shared.time;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("UT · BusinessCalendar")
class BusinessCalendarTest {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("America/Mexico_City");
    private static final ZoneId CANCUN = ZoneId.of("America/Cancun");
    private final TenantCalendar calendar = BusinessCalendar.fixed(BUSINESS_ZONE).current();

    @Test
    @DisplayName("La zona del calendario es la del restaurante resuelto")
    void zoneIsResolvedZone() {
        assertThat(calendar.zone()).isEqualTo(ZoneId.of("America/Mexico_City"));
    }

    @Test
    @DisplayName("Cada current() vuelve a resolver la zona: no se queda con la del restaurante anterior")
    void currentResolvesZoneOnEveryCall() {
        AtomicReference<ZoneId> activeTenantZone = new AtomicReference<>(BUSINESS_ZONE);
        BusinessCalendar perTenant = new BusinessCalendar(activeTenantZone::get, Clock.systemUTC());

        assertThat(perTenant.current().zone()).isEqualTo(BUSINESS_ZONE);
        activeTenantZone.set(CANCUN);
        assertThat(perTenant.current().zone()).isEqualTo(CANCUN);
    }

    @Nested
    @DisplayName("Corte de día después de las 18:00 locales (UTC vs Negocio)")
    class DayCutoffTests {

        @Test
        @DisplayName("A las 19:00 horas locales de CDMX (01:00 UTC del día siguiente), el día de negocio sigue siendo el mismo día local")
        void businessDayPreservedAfterSixPmLocal() {
            LocalDate businessDay = LocalDate.of(2026, 10, 20);
            Instant dinnerTime = businessDay.atTime(19, 0).atZone(BUSINESS_ZONE).toInstant();
            TenantCalendar atDinnerTime = new BusinessCalendar(
                    () -> BUSINESS_ZONE, Clock.fixed(dinnerTime, ZoneOffset.UTC)).current();

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

        @Test
        @DisplayName("A las 23:30 de CDMX, Cancún ya está en el día siguiente: cada restaurante corta en su medianoche")
        void sameInstantFallsOnDifferentDaysPerRestaurant() {
            LocalDate day = LocalDate.of(2026, 10, 20);
            Instant lateDinner = day.atTime(23, 30).atZone(BUSINESS_ZONE).toInstant();

            TenantCalendar cdmx = new BusinessCalendar(() -> BUSINESS_ZONE, Clock.fixed(lateDinner, ZoneOffset.UTC)).current();
            TenantCalendar cancun = new BusinessCalendar(() -> CANCUN, Clock.fixed(lateDinner, ZoneOffset.UTC)).current();

            assertThat(cdmx.today()).isEqualTo(day);
            assertThat(cancun.today()).isEqualTo(day.plusDays(1));
            assertThat(lateDinner).isBefore(cdmx.toInstantRange(day, day).endExclusive());
            assertThat(lateDinner).isAfterOrEqualTo(cancun.toInstantRange(day.plusDays(1), day.plusDays(1)).start());
        }
    }

    @Nested
    @DisplayName("Horario de verano: el rango conserva días de calendario completos")
    class DaylightSavingTests {

        private final TenantCalendar tijuana = BusinessCalendar.fixed(ZoneId.of("America/Tijuana")).current();

        @Test
        @DisplayName("El día en que se adelanta la hora dura 23 horas y empieza en la medianoche local")
        void springForwardDayLastsTwentyThreeHours() {
            LocalDate springForward = LocalDate.of(2026, 3, 8);

            InstantRange range = tijuana.toInstantRange(springForward, springForward);

            assertThat(Duration.between(range.start(), range.endExclusive())).isEqualTo(Duration.ofHours(23));
            assertThat(range.start().atZone(tijuana.zone()).toLocalTime()).isEqualTo(LocalTime.MIDNIGHT);
            assertThat(range.endExclusive().atZone(tijuana.zone()).toLocalDate()).isEqualTo(springForward.plusDays(1));
        }

        @Test
        @DisplayName("El día en que se atrasa la hora dura 25 horas y termina en la siguiente medianoche local")
        void fallBackDayLastsTwentyFiveHours() {
            LocalDate fallBack = LocalDate.of(2026, 11, 1);

            InstantRange range = tijuana.toInstantRange(fallBack, fallBack);

            assertThat(Duration.between(range.start(), range.endExclusive())).isEqualTo(Duration.ofHours(25));
            assertThat(range.endExclusive().atZone(tijuana.zone()).toLocalTime()).isEqualTo(LocalTime.MIDNIGHT);
        }
    }

    @Nested
    @DisplayName("Zonas que puede configurar un restaurante")
    class SupportedZonesTests {

        @ParameterizedTest
        @ValueSource(strings = {"America/Mexico_City", "America/Cancun", "America/Tijuana", "Europe/Madrid"})
        @DisplayName("Acepta IDs IANA geográficos")
        void acceptsGeographicIanaIds(String zone) {
            assertThat(RestaurantTimeZones.isSupported(zone)).isTrue();
        }

        @ParameterizedTest
        @ValueSource(strings = {"Marte/Olympus", "-05:00", "UTC-5", "UTC", "Etc/GMT+5", "EST", "Mexico/General",
                "america/cancun", ""})
        @DisplayName("Rechaza IDs inexistentes, offsets fijos y alias heredados")
        void rejectsOffsetsAliasesAndUnknownIds(String zone) {
            assertThat(RestaurantTimeZones.isSupported(zone)).isFalse();
        }

        @Test
        @DisplayName("Rechaza null")
        void rejectsNull() {
            assertThat(RestaurantTimeZones.isSupported(null)).isFalse();
        }
    }
}
