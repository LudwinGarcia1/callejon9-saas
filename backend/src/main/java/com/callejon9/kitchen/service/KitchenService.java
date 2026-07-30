package com.callejon9.kitchen.service;

import com.callejon9.kitchen.event.KitchenItemStatusChangedEvent;
import com.callejon9.kitchen.web.dto.KitchenItemResponse;
import com.callejon9.order.domain.KitchenItemStatus;
import com.callejon9.order.domain.Order;
import com.callejon9.order.domain.OrderItem;
import com.callejon9.order.domain.OrderStatus;
import com.callejon9.order.repository.OrderItemRepository;
import com.callejon9.order.repository.OrderRepository;
import com.callejon9.order.service.OrderWithItems;
import com.callejon9.shared.error.BusinessRuleException;
import com.callejon9.shared.error.ResourceNotFoundException;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * El tablero de cocina: lista las ordenes enviadas y avanza el
 * {@code kitchenStatus} de cada producto.
 *
 * El TenantContext ya esta fijado por TenantFilter antes de que la peticion
 * llegue aqui, asi que @Transactional declarativo alcanza (mismo patron que
 * OrderService y TableService).
 */
@Service
public class KitchenService {

    /**
     * Orden estricto de avance. Cualquier salto (PENDING -> READY) o
     * retroceso (READY -> PENDING) queda fuera de esta secuencia y se
     * rechaza con 409.
     */
    private static final List<KitchenItemStatus> FORWARD_SEQUENCE = List.of(
            KitchenItemStatus.PENDING,
            KitchenItemStatus.IN_PREPARATION,
            KitchenItemStatus.READY,
            KitchenItemStatus.DELIVERED);

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ApplicationEventPublisher eventPublisher;

    public KitchenService(
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            ApplicationEventPublisher eventPublisher) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(readOnly = true)
    public List<OrderWithItems> listSentOrders() {
        return orderRepository.findByStatusOrderBySentToKitchenAtAsc(OrderStatus.SENT).stream()
                .map(order -> new OrderWithItems(
                        order, orderItemRepository.findByOrderIdOrderByOrderedAt(order.getId())))
                .toList();
    }

    @Transactional
    public OrderItem advanceItemStatus(UUID itemId, KitchenItemStatus newStatus) {
        OrderItem item = orderItemRepository.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "El producto de la orden " + itemId + " no existe."));

        validateForwardTransition(item.getKitchenStatus(), newStatus);

        item.setKitchenStatus(newStatus);
        orderItemRepository.save(item);

        eventPublisher.publishEvent(new KitchenItemStatusChangedEvent(
                item.getTenantId(), KitchenItemResponse.from(item)));

        promoteOrderToReadyIfEveryItemIsReady(item.getOrderId());

        return item;
    }

    private void validateForwardTransition(KitchenItemStatus current, KitchenItemStatus next) {
        int currentIndex = FORWARD_SEQUENCE.indexOf(current);
        int nextIndex = FORWARD_SEQUENCE.indexOf(next);
        if (nextIndex != currentIndex + 1) {
            throw new BusinessRuleException(
                    "No se puede mover el producto de " + current + " a " + next + ".");
        }
    }

    /**
     * Un producto DELIVERED ya paso por READY en algun momento de su ciclo de
     * vida, asi que tambien cuenta para decidir si la orden completa esta
     * lista.
     */
    private void promoteOrderToReadyIfEveryItemIsReady(UUID orderId) {
        List<OrderItem> items = orderItemRepository.findByOrderIdOrderByOrderedAt(orderId);
        boolean everyItemIsReadyOrBeyond = items.stream()
                .allMatch(item -> item.getKitchenStatus() == KitchenItemStatus.READY
                        || item.getKitchenStatus() == KitchenItemStatus.DELIVERED);
        if (!everyItemIsReadyOrBeyond) {
            return;
        }

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("La orden " + orderId + " no existe."));
        if (order.getStatus() == OrderStatus.SENT) {
            order.setStatus(OrderStatus.READY);
            orderRepository.save(order);
        }
    }
}
