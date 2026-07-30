package com.callejon9.order.service;

import com.callejon9.catalog.domain.Product;
import com.callejon9.catalog.repository.ProductRepository;
import com.callejon9.order.domain.KitchenItemStatus;
import com.callejon9.order.domain.Order;
import com.callejon9.order.domain.OrderItem;
import com.callejon9.order.domain.OrderStatus;
import com.callejon9.order.repository.OrderItemRepository;
import com.callejon9.order.repository.OrderRepository;
import com.callejon9.shared.error.BusinessRuleException;
import com.callejon9.shared.error.ResourceNotFoundException;
import com.callejon9.table.service.TableService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * El TenantContext ya esta fijado por TenantFilter antes de que la peticion
 * llegue aqui, asi que @Transactional declarativo alcanza: a diferencia de
 * TenantOnboardingService, no se cruza la frontera control plane / data plane.
 */
@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductRepository productRepository;
    private final TableService tableService;
    private final FolioGenerator folioGenerator;

    public OrderService(
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            ProductRepository productRepository,
            TableService tableService,
            FolioGenerator folioGenerator) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.productRepository = productRepository;
        this.tableService = tableService;
        this.folioGenerator = folioGenerator;
    }

    @Transactional
    public Order openOrder(UUID tableId, int guestCount, UUID waiterId) {
        // Ocupar la mesa PRIMERO: si esta ocupada, el 409 sale de aqui y la
        // orden nunca se crea.
        tableService.occupy(tableId, waiterId);

        Instant now = Instant.now();
        return orderRepository.save(Order.builder()
                .folio(folioGenerator.next())
                .tableId(tableId)
                .waiterId(waiterId)
                .guestCount(guestCount)
                .status(OrderStatus.NEW)
                .total(BigDecimal.ZERO)
                .openedAt(now)
                .build());
    }

    @Transactional(readOnly = true)
    public OrderWithItems getOrder(UUID orderId) {
        Order order = requireOrder(orderId);
        List<OrderItem> items = orderItemRepository.findByOrderIdOrderByOrderedAt(orderId);
        return new OrderWithItems(order, items);
    }

    @Transactional(readOnly = true)
    public List<Order> listOrders(OrderStatus status) {
        if (status != null) {
            return orderRepository.findByStatusOrderByOpenedAtDesc(status);
        }
        return orderRepository.findAllByOrderByOpenedAtDesc();
    }

    @Transactional
    public OrderWithItems addItems(UUID orderId, List<NewOrderItem> newItems) {
        Order order = requireOrder(orderId);

        if (order.getStatus() == OrderStatus.PAID || order.getStatus() == OrderStatus.CANCELED) {
            throw new BusinessRuleException(
                    "La orden " + order.getFolio() + " ya esta " + order.getStatus()
                            + " y no admite mas productos.");
        }

        Instant now = Instant.now();
        List<OrderItem> toSave = newItems.stream()
                .map(newItem -> toOrderItem(order.getId(), newItem, now))
                .toList();
        orderItemRepository.saveAll(toSave);

        List<OrderItem> allItems = orderItemRepository.findByOrderIdOrderByOrderedAt(orderId);
        order.setTotal(sumOf(allItems));
        orderRepository.save(order);

        return new OrderWithItems(order, allItems);
    }

    @Transactional
    public OrderWithItems sendToKitchen(UUID orderId) {
        Order order = requireOrder(orderId);

        if (order.getStatus() != OrderStatus.NEW) {
            throw new BusinessRuleException(
                    "La orden " + order.getFolio() + " no esta en estado NEW.");
        }

        List<OrderItem> items = orderItemRepository.findByOrderIdOrderByOrderedAt(orderId);
        if (items.isEmpty()) {
            throw new BusinessRuleException(
                    "La orden " + order.getFolio() + " no tiene productos.");
        }

        order.setStatus(OrderStatus.SENT);
        order.setSentToKitchenAt(Instant.now());
        orderRepository.save(order);

        items.forEach(item -> item.setKitchenStatus(KitchenItemStatus.PENDING));
        orderItemRepository.saveAll(items);

        return new OrderWithItems(order, items);
    }

    private OrderItem toOrderItem(UUID orderId, NewOrderItem newItem, Instant now) {
        Product product = productRepository.findById(newItem.productId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "El producto " + newItem.productId() + " no existe."));

        return OrderItem.builder()
                .orderId(orderId)
                .productId(product.getId())
                .productName(product.getName())
                .unitPrice(product.getPrice())
                .quantity(newItem.quantity())
                .kitchenStatus(KitchenItemStatus.PENDING)
                .notes(newItem.notes())
                .orderedAt(now)
                .build();
    }

    private BigDecimal sumOf(List<OrderItem> items) {
        return items.stream()
                .map(item -> item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private Order requireOrder(UUID orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("La orden no existe."));
    }
}
