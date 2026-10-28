package com.callejon9.order.service;

import com.callejon9.catalog.domain.Product;
import com.callejon9.catalog.repository.ProductRepository;
import com.callejon9.order.domain.KitchenItemStatus;
import com.callejon9.order.domain.Order;
import com.callejon9.order.domain.OrderItem;
import com.callejon9.order.domain.OrderStatus;
import com.callejon9.order.event.OrderSentToKitchenEvent;
import com.callejon9.order.repository.OrderItemRepository;
import com.callejon9.order.repository.OrderRepository;
import com.callejon9.order.web.dto.OrderResponse;
import com.callejon9.shared.error.BusinessRuleException;
import com.callejon9.shared.error.ResourceNotFoundException;
import com.callejon9.shared.web.InvalidPageRequestException;
import com.callejon9.table.service.TableService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * El TenantContext ya esta fijado por TenantFilter antes de que la peticion
 * llegue aqui, asi que @Transactional declarativo alcanza: a diferencia de
 * TenantOnboardingService, no se cruza la frontera control plane / data plane.
 */
@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    /** Ordenes que siguen en servicio: todavia no se cobran ni se cancelan. */
    static final List<OrderStatus> OPEN_STATUSES = List.of(OrderStatus.NEW, OrderStatus.SENT, OrderStatus.READY);

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductRepository productRepository;
    private final TableService tableService;
    private final FolioGenerator folioGenerator;
    private final ApplicationEventPublisher eventPublisher;

    public OrderService(
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            ProductRepository productRepository,
            TableService tableService,
            FolioGenerator folioGenerator,
            ApplicationEventPublisher eventPublisher) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.productRepository = productRepository;
        this.tableService = tableService;
        this.folioGenerator = folioGenerator;
        this.eventPublisher = eventPublisher;
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
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("La orden no existe."));
        List<OrderItem> items = orderItemRepository.findByOrderIdOrderByOrderedAt(orderId);
        return new OrderWithItems(order, items);
    }

    /**
     * Una pagina de ordenes (CAL-16). {@code openOnly} deja solo las que
     * siguen en servicio -- lo que piden mesero, caja y la app movil --, para
     * que no tengan que traer el historial completo y filtrarlo en el cliente.
     */
    @Transactional(readOnly = true)
    public Page<Order> listOrders(OrderStatus status, boolean openOnly, Pageable pageable) {
        if (status != null && openOnly) {
            throw new InvalidPageRequestException("Usa status u open, no ambos.");
        }
        if (openOnly) {
            return orderRepository.findByStatusIn(OPEN_STATUSES, pageable);
        }
        if (status != null) {
            return orderRepository.findByStatus(status, pageable);
        }
        return orderRepository.findAll(pageable);
    }

    @Transactional
    public OrderWithItems addItems(UUID orderId, List<NewOrderItem> newItems, OrderActor actor) {
        Order order = requireOrder(orderId);
        requireOwnership(order, actor);

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

        // Lo que se pide despues del envio tambien hay que prepararlo: una
        // orden READY vuelve a SENT para reaparecer en el tablero de cocina
        // (que solo lista SENT) y se anuncia de nuevo con sus items PENDING.
        boolean alreadyInKitchen = order.getStatus() == OrderStatus.SENT
                || order.getStatus() == OrderStatus.READY;
        if (alreadyInKitchen) {
            order.setStatus(OrderStatus.SENT);
        }

        List<OrderItem> allItems = orderItemRepository.findByOrderIdOrderByOrderedAt(orderId);
        order.setTotal(sumOf(allItems));
        orderRepository.save(order);

        OrderWithItems result = new OrderWithItems(order, allItems);
        if (alreadyInKitchen) {
            eventPublisher.publishEvent(
                    new OrderSentToKitchenEvent(order.getTenantId(), OrderResponse.from(result)));
        }
        return result;
    }

    /**
     * Cancela una orden y libera su mesa, todo en una sola transaccion: si
     * algo falla a la mitad, nada de esto debe quedar a medias (mismo
     * criterio que {@code CheckoutService.checkout}). Las lineas de la orden
     * se conservan intactas para el registro; solo cambia el estado.
     *
     * <p>Un mesero solo cancela su propia orden y solo mientras sigue en NEW.
     * Una vez enviada a cocina el producto ya se preparo o se esta
     * preparando: cancelarla libera la mesa sin venta, asi que esa decision
     * queda reservada al administrador y se registra quien la tomo.
     */
    @Transactional
    public OrderWithItems cancelOrder(UUID orderId, OrderActor actor) {
        Order order = requireOrder(orderId);
        requireOwnership(order, actor);

        if (order.getStatus() == OrderStatus.PAID || order.getStatus() == OrderStatus.CANCELED) {
            throw new BusinessRuleException(
                    "La orden " + order.getFolio() + " ya esta " + order.getStatus()
                            + " y no se puede cancelar.");
        }

        if (order.getStatus() != OrderStatus.NEW && !actor.admin()) {
            throw new AccessDeniedException(
                    "La orden " + order.getFolio() + " ya se envio a cocina; "
                            + "solo un administrador puede cancelarla.");
        }

        log.info("Orden cancelada: folio={} estadoPrevio={} total={} usuario={} admin={}",
                order.getFolio(), order.getStatus(), order.getTotal(),
                actor.userId(), actor.admin());

        order.setStatus(OrderStatus.CANCELED);
        order.setClosedAt(Instant.now());
        orderRepository.save(order);

        if (order.getTableId() != null) {
            tableService.free(order.getTableId());
        }

        List<OrderItem> items = orderItemRepository.findByOrderIdOrderByOrderedAt(orderId);
        return new OrderWithItems(order, items);
    }

    @Transactional
    public OrderWithItems sendToKitchen(UUID orderId, OrderActor actor) {
        Order order = requireOrder(orderId);
        requireOwnership(order, actor);

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

        OrderWithItems result = new OrderWithItems(order, items);
        eventPublisher.publishEvent(
                new OrderSentToKitchenEvent(order.getTenantId(), OrderResponse.from(result)));
        return result;
    }

    private OrderItem toOrderItem(UUID orderId, NewOrderItem newItem, Instant now) {
        Product product = productRepository.findById(newItem.productId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "El producto " + newItem.productId() + " no existe."));

        if (!product.isActive()) {
            throw new BusinessRuleException("El producto " + product.getName() + " esta inactivo.");
        }

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

    /**
     * RLS ya garantiza que la orden es del restaurante; esto acota dentro del
     * restaurante. El rol WAITER basta para llegar al endpoint, pero no para
     * modificar la orden de otro mesero: solo ADMIN opera sobre cualquiera.
     */
    private void requireOwnership(Order order, OrderActor actor) {
        if (!actor.admin() && !actor.userId().equals(order.getWaiterId())) {
            throw new AccessDeniedException(
                    "La orden " + order.getFolio() + " pertenece a otro mesero.");
        }
    }

    private Order requireOrder(UUID orderId) {
        return orderRepository.findForCheckout(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("La orden no existe."));
    }
}
