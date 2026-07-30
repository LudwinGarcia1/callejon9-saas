package com.callejon9.order.service;

import com.callejon9.order.domain.Order;
import com.callejon9.order.domain.OrderItem;
import java.util.List;

/** Una orden junto con sus lineas, tal como la necesita el detalle en la API. */
public record OrderWithItems(Order order, List<OrderItem> items) {
}
