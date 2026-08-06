package com.callejon9.inventory.domain;

/**
 * Nivel derivado del stock, calculado por la entidad y enviado en la
 * respuesta para que la interfaz no recalcule umbrales de negocio.
 *
 * NEGATIVE es un estado propio, no un caso de LOW: un stock negativo no es
 * "se esta acabando", es la senal de que el conteo fisico esta mal y hay que
 * corregirlo.
 */
public enum StockLevel {
    OK,
    LOW,
    NEGATIVE
}
