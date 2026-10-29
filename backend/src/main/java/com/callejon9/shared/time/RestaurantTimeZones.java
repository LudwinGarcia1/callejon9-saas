package com.callejon9.shared.time;

import java.time.ZoneId;
import java.util.List;

/**
 * Que zona horaria puede configurar un restaurante.
 *
 * Solo IDs IANA geograficos (America/Cancun, Europe/Madrid): son los que
 * siguen los cambios de horario de verano de un lugar real. Se rechazan los
 * offsets fijos (-05:00, UTC-5, Etc/GMT+5) y los alias heredados (EST,
 * US/Eastern, Mexico/General), porque congelan una regla que el pais puede
 * cambiar o no la tuvo nunca.
 */
public final class RestaurantTimeZones {

    private static final List<String> GEOGRAPHIC_AREAS = List.of(
            "Africa/", "America/", "Antarctica/", "Asia/", "Atlantic/",
            "Australia/", "Europe/", "Indian/", "Pacific/");

    private RestaurantTimeZones() {
    }

    public static boolean isSupported(String zoneId) {
        return zoneId != null
                && GEOGRAPHIC_AREAS.stream().anyMatch(zoneId::startsWith)
                && ZoneId.getAvailableZoneIds().contains(zoneId);
    }
}
