package com.callejon9.table.web.dto;

import jakarta.validation.constraints.NotNull;

/** Alta o baja logica de una mesa. No toca su estado de servicio. */
public record UpdateTableActiveRequest(@NotNull Boolean active) {
}
