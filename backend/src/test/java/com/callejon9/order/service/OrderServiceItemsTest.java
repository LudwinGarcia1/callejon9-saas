package com.callejon9.order.service;

import com.callejon9.catalog.domain.Product;
import com.callejon9.catalog.repository.ProductRepository;
import com.callejon9.order.domain.Order;
import com.callejon9.order.domain.OrderItem;
import com.callejon9.order.domain.OrderStatus;
import com.callejon9.order.event.OrderSentToKitchenEvent;
import com.callejon9.order.repository.OrderItemRepository;
import com.callejon9.order.repository.OrderRepository;
import com.callejon9.shared.error.BusinessRuleException;
import com.callejon9.shared.error.ResourceNotFoundException;
import com.callejon9.table.service.TableService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Agregar productos a una orden: el total lo calcula el servidor con sus
 * precios (el cliente solo envía producto y cantidad) y un producto inactivo
 * no entra a la comanda (CAL-10).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("UT · OrderService.addItems")
class OrderServiceItemsTest {

    @Mock private OrderRepository orderRepository;
    @Mock private OrderItemRepository orderItemRepository;
    @Mock private ProductRepository productRepository;
    @Mock private TableService tableService;
    @Mock private FolioGenerator folioGenerator;
    @Mock private ApplicationEventPublisher eventPublisher;

    private OrderService service;
    private final UUID waiterId = UUID.randomUUID();
    private Order order;
    private final List<OrderItem> stored = new ArrayList<>();

    @BeforeEach
    void setUp() {
        service = new OrderService(orderRepository, orderItemRepository, productRepository, tableService,
                folioGenerator, eventPublisher);
        order = Order.builder().folio("ORD-261021184954").waiterId(waiterId).guestCount(2)
                .status(OrderStatus.NEW).total(BigDecimal.ZERO).openedAt(Instant.now()).build();
        order.setId(UUID.randomUUID());
        order.setTenantId(UUID.randomUUID());
        stored.clear();
        when(orderRepository.findForCheckout(order.getId())).thenReturn(Optional.of(order));
        when(orderItemRepository.saveAll(anyList())).thenAnswer(invocation -> {
            List<OrderItem> items = invocation.getArgument(0);
            stored.addAll(items);
            return items;
        });
        when(orderItemRepository.findByOrderIdOrderByOrderedAt(order.getId())).thenAnswer(invocation -> List.copyOf(stored));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private Product product(String name, String price, boolean active) {
        Product product = Product.builder().name(name).price(new BigDecimal(price)).active(active).build();
        product.setId(UUID.randomUUID());
        when(productRepository.findById(product.getId())).thenReturn(Optional.of(product));
        return product;
    }

    private OrderActor waiter() {
        return new OrderActor(waiterId, false);
    }

    @Test
    @DisplayName("el total suma precio del servidor × cantidad de varios productos")
    void totalUsesServerPricesAndQuantities() {
        Product guacamole = product("Guacamole con totopos", "95.00", true);
        Product margarita = product("Margarita", "135.00", true);

        OrderWithItems result = service.addItems(order.getId(), List.of(
                new NewOrderItem(guacamole.getId(), 2, null),
                new NewOrderItem(margarita.getId(), 1, "sin sal")), waiter());

        assertThat(result.order().getTotal()).isEqualByComparingTo("325.00");
        assertThat(result.items()).extracting(OrderItem::getUnitPrice)
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("95.00"), new BigDecimal("135.00"));
        assertThat(result.items()).extracting(OrderItem::getProductName)
                .containsExactly("Guacamole con totopos", "Margarita");
        assertThat(result.items().get(1).getNotes()).isEqualTo("sin sal");
    }

    @Test
    @DisplayName("un segundo lote se suma a lo que la orden ya tenía")
    void secondBatchAddsToTheExistingTotal() {
        Product sopa = product("Sopa de tortilla", "85.00", true);
        Product flan = product("Flan napolitano", "75.00", true);
        service.addItems(order.getId(), List.of(new NewOrderItem(sopa.getId(), 1, null)), waiter());

        OrderWithItems result = service.addItems(order.getId(), List.of(new NewOrderItem(flan.getId(), 3, null)), waiter());

        assertThat(result.order().getTotal()).isEqualByComparingTo("310.00");
        assertThat(result.items()).hasSize(2);
    }

    @Test
    @DisplayName("el precio queda congelado en la línea aunque el producto cambie después")
    void priceIsSnapshottedOnTheLine() {
        Product margarita = product("Margarita", "135.00", true);
        service.addItems(order.getId(), List.of(new NewOrderItem(margarita.getId(), 1, null)), waiter());

        margarita.setPrice(new BigDecimal("150.00"));

        assertThat(stored.get(0).getUnitPrice()).isEqualByComparingTo("135.00");
    }

    @Test
    @DisplayName("un producto inactivo se rechaza y no se guarda ninguna línea del lote (CAL-10)")
    void inactiveProductIsRejected() {
        Product sopa = product("Sopa de tortilla", "85.00", true);
        Product retirado = product("Pozole de temporada", "120.00", false);

        assertThatThrownBy(() -> service.addItems(order.getId(), List.of(
                new NewOrderItem(sopa.getId(), 1, null),
                new NewOrderItem(retirado.getId(), 1, null)), waiter()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("El producto Pozole de temporada esta inactivo.");
        verify(orderItemRepository, never()).saveAll(anyList());
        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("un producto que no existe en el restaurante se rechaza con 404")
    void unknownProductIsRejected() {
        UUID unknown = UUID.randomUUID();
        when(productRepository.findById(unknown)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.addItems(order.getId(), List.of(new NewOrderItem(unknown, 1, null)), waiter()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("no existe");
        verify(orderItemRepository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("una orden pagada o cancelada no admite más productos")
    void closedOrdersRejectNewItems() {
        Product sopa = product("Sopa de tortilla", "85.00", true);
        for (OrderStatus closed : List.of(OrderStatus.PAID, OrderStatus.CANCELED)) {
            order.setStatus(closed);
            assertThatThrownBy(() -> service.addItems(order.getId(), List.of(new NewOrderItem(sopa.getId(), 1, null)), waiter()))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("no admite mas productos");
        }
        verify(orderItemRepository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("agregar a una orden lista la regresa a cocina y la anuncia otra vez")
    void addingToAReadyOrderSendsItBackToTheKitchen() {
        Product flan = product("Flan napolitano", "75.00", true);
        order.setStatus(OrderStatus.READY);

        OrderWithItems result = service.addItems(order.getId(), List.of(new NewOrderItem(flan.getId(), 1, null)), waiter());

        assertThat(result.order().getStatus()).isEqualTo(OrderStatus.SENT);
        verify(eventPublisher).publishEvent(any(OrderSentToKitchenEvent.class));
    }
}
