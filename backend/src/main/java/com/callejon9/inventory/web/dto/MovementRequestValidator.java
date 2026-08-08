package com.callejon9.inventory.web.dto;

import com.callejon9.inventory.domain.InventoryMovementType;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Cada campo del cuerpo significa una sola cosa, y este validador es lo que lo
 * sostiene: ADJUSTMENT lleva countedStock y nunca quantity; los otros tres
 * llevan quantity y nunca countedStock; WASTE exige motivo.
 *
 * Los mensajes se cuelgan del campo con addPropertyNode para que salgan en el
 * mapa "errors" del ProblemDetail, que es donde el frontend ya los busca.
 */
public class MovementRequestValidator
        implements ConstraintValidator<ValidMovementRequest, RegisterMovementRequest> {

    @Override
    public boolean isValid(RegisterMovementRequest request, ConstraintValidatorContext context) {
        if (request == null || request.movementType() == null) {
            // @NotNull sobre movementType ya reporta ese caso; no se duplica.
            return true;
        }

        context.disableDefaultConstraintViolation();
        boolean valid = true;

        if (request.movementType() == InventoryMovementType.ADJUSTMENT) {
            if (request.countedStock() == null) {
                reject(context, "countedStock", "Un ajuste requiere el conteo fisico.");
                valid = false;
            }
            if (request.quantity() != null) {
                reject(context, "quantity",
                        "Un ajuste no lleva cantidad: el sistema calcula la diferencia.");
                valid = false;
            }
            return valid;
        }

        if (request.quantity() == null) {
            reject(context, "quantity", "La cantidad es obligatoria.");
            valid = false;
        }
        if (request.countedStock() != null) {
            reject(context, "countedStock", "El conteo fisico solo aplica a un ajuste.");
            valid = false;
        }
        if (request.movementType() == InventoryMovementType.WASTE && isBlank(request.reason())) {
            reject(context, "reason", "Una merma requiere motivo.");
            valid = false;
        }
        return valid;
    }

    private void reject(ConstraintValidatorContext context, String field, String message) {
        context.buildConstraintViolationWithTemplate(message)
                .addPropertyNode(field)
                .addConstraintViolation();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
