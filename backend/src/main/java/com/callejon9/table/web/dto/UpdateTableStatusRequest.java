package com.callejon9.table.web.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateTableStatusRequest(@NotNull Boolean active) {
}
