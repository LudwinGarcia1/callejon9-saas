package com.callejon9.table.web.dto;

import com.callejon9.table.domain.TableStatus;
import jakarta.validation.constraints.NotNull;

/**
 * Cambio manual del estado de servicio de una mesa.
 *
 * <p>Acepta el enum completo, incluido {@code OCCUPIED}, y es
 * {@link com.callejon9.table.service.TableService#changeStatus} quien lo
 * rechaza. Validarlo aqui con una lista de valores permitidos duplicaria la
 * regla en dos sitios que pueden separarse.
 */
public record UpdateTableStatusRequest(@NotNull TableStatus status) {
}
