package com.callejon9.auth.web.dto;

import java.util.UUID;

public record LoginResponse(UUID userId, String fullName, String role, boolean twoFactorRequired) {
}
