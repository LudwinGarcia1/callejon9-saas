package com.callejon9.platform.tenant.web;

import com.callejon9.platform.tenant.service.RestaurantSettingsService;
import com.callejon9.platform.tenant.web.dto.RestaurantSettingsResponse;
import com.callejon9.platform.tenant.web.dto.UpdateRestaurantSettingsRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Configuracion del restaurante activo. Sin id en la ruta: el recurso es
 * siempre el del tenant de la sesion.
 */
@RestController
@RequestMapping("/api/v1/settings")
public class RestaurantSettingsController {

    private final RestaurantSettingsService settingsService;

    public RestaurantSettingsController(RestaurantSettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public RestaurantSettingsResponse get() {
        return RestaurantSettingsResponse.from(settingsService.current());
    }

    @PatchMapping
    @PreAuthorize("hasRole('ADMIN')")
    public RestaurantSettingsResponse update(@Valid @RequestBody UpdateRestaurantSettingsRequest request) {
        return RestaurantSettingsResponse.from(settingsService.updateTimezone(request.timezone()));
    }
}
