package com.callejon9.inventory.domain;

import java.math.BigDecimal;

/**
 * Los cuatro tipos que el CHECK de inventory_movements admite.
 *
 * El signo vive aqui porque es lo unico que sabe que significa cada tipo:
 * ponerlo en el servicio repartiria el mismo switch por varios metodos. En
 * IN, OUT y WASTE la cantidad siempre llega positiva y el tipo decide la
 * direccion; ADJUSTMENT es el unico que llega con signo, porque es una
 * diferencia contra el conteo fisico y puede ir en cualquier sentido.
 */
public enum InventoryMovementType {
    IN,
    OUT,
    ADJUSTMENT,
    WASTE;

    public BigDecimal signedEffect(BigDecimal quantity) {
        return switch (this) {
            case IN -> quantity;
            case OUT, WASTE -> quantity.negate();
            case ADJUSTMENT -> quantity;
        };
    }
}
