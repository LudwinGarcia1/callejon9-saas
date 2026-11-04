package com.callejon9.platform.tenant.web.dto;

import com.callejon9.shared.time.RestaurantTimeZone;
import jakarta.validation.constraints.NotBlank;

/**
 * Por ahora solo la zona horaria (CAL-19). Moneda y horario de servicio
 * llegaran a este mismo recurso con CAL-36.
 */
public record UpdateRestaurantSettingsRequest(@NotBlank @RestaurantTimeZone String timezone) {
}
