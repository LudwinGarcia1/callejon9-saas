package com.callejon9.platform.tenant.web.dto;

import com.callejon9.platform.tenant.domain.Tenant;

/** Configuracion del restaurante activo que el ADMIN puede consultar. */
public record RestaurantSettingsResponse(String name, String slug, String timezone) {

    public static RestaurantSettingsResponse from(Tenant tenant) {
        return new RestaurantSettingsResponse(tenant.getName(), tenant.getSlug(), tenant.getTimezone());
    }
}
