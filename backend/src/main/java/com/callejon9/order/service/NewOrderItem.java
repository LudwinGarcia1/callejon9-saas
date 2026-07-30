package com.callejon9.order.service;

import java.util.UUID;

/** Comando de entrada para agregar un producto a una orden abierta. */
public record NewOrderItem(UUID productId, int quantity, String notes) {
}
