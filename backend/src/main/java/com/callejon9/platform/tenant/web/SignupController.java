package com.callejon9.platform.tenant.web;

import com.callejon9.platform.tenant.domain.Tenant;
import com.callejon9.platform.tenant.service.TenantOnboardingService;
import com.callejon9.platform.tenant.web.dto.SignupRequest;
import com.callejon9.platform.tenant.web.dto.SignupResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Alta publica de un restaurante en el SaaS. */
@RestController
@RequestMapping("/api/v1/signup")
public class SignupController {

    private final TenantOnboardingService onboardingService;

    public SignupController(TenantOnboardingService onboardingService) {
        this.onboardingService = onboardingService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SignupResponse signup(@Valid @RequestBody SignupRequest request) {
        Tenant tenant = onboardingService.onboard(
                request.restaurantName(),
                request.slug(),
                request.adminEmail(),
                request.adminFullName(),
                request.password(),
                request.planCode());

        return new SignupResponse(tenant.getId(), tenant.getSlug(), request.adminEmail());
    }
}
