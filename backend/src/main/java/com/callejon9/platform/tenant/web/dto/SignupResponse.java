package com.callejon9.platform.tenant.web.dto;

import java.util.UUID;

public record SignupResponse(UUID tenantId, String slug, String adminEmail) {
}
