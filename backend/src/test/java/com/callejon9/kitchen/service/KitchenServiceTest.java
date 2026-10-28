package com.callejon9.kitchen.service;

import com.callejon9.kitchen.event.KitchenItemStatusChangedEvent;
import com.callejon9.order.domain.KitchenItemStatus;
import com.callejon9.order.domain.Order;
import com.callejon9.order.domain.OrderItem;
import com.callejon9.order.domain.OrderStatus;
import com.callejon9.order.repository.OrderItemRepository;
import com.callejon9.order.repository.OrderRepository;
import com.callejon9.order.service.OrderWithItems;
import com.callejon9.shared.error.BusinessRuleException;
import com.callejon9.shared.error.ResourceNotFoundException;
import com.callejon9.tenancy.TenantContext;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("UT · KitchenService (Cocina y KDS)")
class KitchenServiceTest {

    private static final UUID TENANT_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final UUID ORDER_ID = UUID.fromString("66666666-7777-8888-9999-000000000000");

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private KitchenService kitchenService;

    @BeforeEach
    void setUp() {
        TenantContext.set(TENANT_ID);
        kitchenService = new KitchenService(orderRepository, orderItemRepository, eventPublisher);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    /**
     * Las 13 combinaciones de estado que no son el siguiente paso de la
     * secuencia: saltos, retrocesos, el mismo estado y cualquier salida de
     * DELIVERED. Solo PENDING -> IN_PREPARATION -> READY -> DELIVERED queda fuera.
     */
    static Stream<Arguments> invalidTransitions() {
        KitchenItemStatus[] sequence = KitchenItemStatus.values();
        List<Arguments> pairs = new ArrayList<>();
        for (int from = 0; from < sequence.length; from++) {
            for (int to = 0; to < sequence.length; to++) {
                if (to != from + 1) {
                    pairs.add(Arguments.of(sequence[from], sequence[to]));
                }
            }
        }
        return pairs.stream();
    }

    private OrderItem buildItem(UUID itemId, KitchenItemStatus status) {
        OrderItem item = new OrderItem();
        item.setId(itemId);
        item.setOrderId(ORDER_ID);
        item.setTenantId(TENANT_ID);
        item.setProductId(UUID.randomUUID());
        item.setProductName("Hamburguesa con Papas");
        item.setUnitPrice(BigDecimal.valueOf(150.00));
        item.setQuantity(1);
        item.setKitchenStatus(status);
        item.setOrderedAt(Instant.now());
        return item;
    }

    private Order buildOrder(OrderStatus status) {
        Order order = new Order();
        order.setId(ORDER_ID);
        order.setTenantId(TENANT_ID);
        order.setFolio("ORD-2026-KTC");
        order.setStatus(status);
        order.setSentToKitchenAt(Instant.now());
        return order;
    }

    @Nested
    @DisplayName("listSentOrders — Listado de comandas en cocina")
    class ListSentOrdersTests {

        @Test
        @DisplayName("Retorna las órdenes enviadas a cocina con sus partidas")
        void returnsSentOrdersWithItems() {
            Order order = buildOrder(OrderStatus.SENT);
            OrderItem item = buildItem(UUID.randomUUID(), KitchenItemStatus.PENDING);

            when(orderRepository.findByStatusOrderBySentToKitchenAtAsc(OrderStatus.SENT))
                    .thenReturn(List.of(order));
            when(orderItemRepository.findByOrderIdOrderByOrderedAt(ORDER_ID))
                    .thenReturn(List.of(item));

            List<OrderWithItems> result = kitchenService.listSentOrders();

            assertThat(result).hasSize(1);
            assertThat(result.getFirst().order().getId()).isEqualTo(ORDER_ID);
            assertThat(result.getFirst().items()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("advanceItemStatus — Transiciones de estado de ítems de cocina")
    class AdvanceItemStatusTests {

        @Test
        @DisplayName("Transición válida: PENDING -> IN_PREPARATION")
        void pendingToInPreparationSuccess() {
            UUID itemId = UUID.randomUUID();
            OrderItem item = buildItem(itemId, KitchenItemStatus.PENDING);
            Order order = buildOrder(OrderStatus.SENT);

            when(orderItemRepository.findById(itemId)).thenReturn(Optional.of(item));
            when(orderRepository.findForCheckout(ORDER_ID)).thenReturn(Optional.of(order));
            when(orderItemRepository.findByOrderIdOrderByOrderedAt(ORDER_ID)).thenReturn(List.of(item));

            OrderItem updated = kitchenService.advanceItemStatus(itemId, KitchenItemStatus.IN_PREPARATION);

            assertThat(updated.getKitchenStatus()).isEqualTo(KitchenItemStatus.IN_PREPARATION);
            verify(orderItemRepository).save(item);
            verify(eventPublisher).publishEvent(any(KitchenItemStatusChangedEvent.class));
            // La orden aún no pasa a READY porque el producto está en preparación
            assertThat(order.getStatus()).isEqualTo(OrderStatus.SENT);
        }

        @Test
        @DisplayName("Transición válida: IN_PREPARATION -> READY y promueve la orden cuando todos los productos están listos")
        void inPreparationToReadyPromotesOrderToReady() {
            UUID itemId1 = UUID.randomUUID();
            UUID itemId2 = UUID.randomUUID();

            OrderItem item1 = buildItem(itemId1, KitchenItemStatus.IN_PREPARATION);
            OrderItem item2 = buildItem(itemId2, KitchenItemStatus.READY);
            Order order = buildOrder(OrderStatus.SENT);

            when(orderItemRepository.findById(itemId1)).thenReturn(Optional.of(item1));
            when(orderRepository.findForCheckout(ORDER_ID)).thenReturn(Optional.of(order));
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
            // Al avanzar item1 a READY, todos los ítems (item1 e item2) quedan en READY
            when(orderItemRepository.findByOrderIdOrderByOrderedAt(ORDER_ID)).thenReturn(List.of(item1, item2));

            OrderItem updated = kitchenService.advanceItemStatus(itemId1, KitchenItemStatus.READY);

            assertThat(updated.getKitchenStatus()).isEqualTo(KitchenItemStatus.READY);
            verify(orderItemRepository).save(item1);
            // La orden completa se promueve a READY
            assertThat(order.getStatus()).isEqualTo(OrderStatus.READY);
            verify(orderRepository).save(order);
        }

        @Test
        @DisplayName("Transición válida: READY -> DELIVERED")
        void readyToDeliveredSuccess() {
            UUID itemId = UUID.randomUUID();
            OrderItem item = buildItem(itemId, KitchenItemStatus.READY);
            Order order = buildOrder(OrderStatus.READY);

            when(orderItemRepository.findById(itemId)).thenReturn(Optional.of(item));
            when(orderRepository.findForCheckout(ORDER_ID)).thenReturn(Optional.of(order));
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
            when(orderItemRepository.findByOrderIdOrderByOrderedAt(ORDER_ID)).thenReturn(List.of(item));

            OrderItem updated = kitchenService.advanceItemStatus(itemId, KitchenItemStatus.DELIVERED);

            assertThat(updated.getKitchenStatus()).isEqualTo(KitchenItemStatus.DELIVERED);
            verify(orderItemRepository).save(item);
        }

        @Test
        @DisplayName("Rechazo de salto inválido: PENDING -> READY (sin pasar por preparación)")
        void throwsBusinessRuleOnSkippedTransition() {
            UUID itemId = UUID.randomUUID();
            OrderItem item = buildItem(itemId, KitchenItemStatus.PENDING);
            Order order = buildOrder(OrderStatus.SENT);

            when(orderItemRepository.findById(itemId)).thenReturn(Optional.of(item));
            when(orderRepository.findForCheckout(ORDER_ID)).thenReturn(Optional.of(order));

            assertThatThrownBy(() -> kitchenService.advanceItemStatus(itemId, KitchenItemStatus.READY))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("No se puede mover el producto de PENDING a READY");

            verify(orderItemRepository, never()).save(any());
            verify(eventPublisher, never()).publishEvent(any());
        }

        @Test
        @DisplayName("Rechazo de retroceso: READY -> PENDING")
        void throwsBusinessRuleOnBackwardsTransition() {
            UUID itemId = UUID.randomUUID();
            OrderItem item = buildItem(itemId, KitchenItemStatus.READY);
            Order order = buildOrder(OrderStatus.READY);

            when(orderItemRepository.findById(itemId)).thenReturn(Optional.of(item));
            when(orderRepository.findForCheckout(ORDER_ID)).thenReturn(Optional.of(order));

            assertThatThrownBy(() -> kitchenService.advanceItemStatus(itemId, KitchenItemStatus.PENDING))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("No se puede mover el producto de READY a PENDING");

            verify(orderItemRepository, never()).save(any());
            verify(eventPublisher, never()).publishEvent(any());
        }

        @Test
        @DisplayName("Lanza ResourceNotFoundException si el ítem no existe")
        void throwsNotFoundWhenItemDoesNotExist() {
            UUID itemId = UUID.randomUUID();
            when(orderItemRepository.findById(itemId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> kitchenService.advanceItemStatus(itemId, KitchenItemStatus.IN_PREPARATION))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("no existe");
        }

        @ParameterizedTest(name = "{0} -> {1} se rechaza")
        @MethodSource("com.callejon9.kitchen.service.KitchenServiceTest#invalidTransitions")
        @DisplayName("Borde: toda transición que no es el siguiente paso se rechaza y no guarda ni publica")
        void everyNonAdjacentTransitionIsRejected(KitchenItemStatus from, KitchenItemStatus to) {
            UUID itemId = UUID.randomUUID();
            OrderItem item = buildItem(itemId, from);

            when(orderItemRepository.findById(itemId)).thenReturn(Optional.of(item));
            when(orderRepository.findForCheckout(ORDER_ID)).thenReturn(Optional.of(buildOrder(OrderStatus.SENT)));

            assertThatThrownBy(() -> kitchenService.advanceItemStatus(itemId, to))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("No se puede mover el producto de " + from + " a " + to);

            assertThat(item.getKitchenStatus()).isEqualTo(from);
            verify(orderItemRepository, never()).save(any());
            verify(eventPublisher, never()).publishEvent(any());
        }

        @Test
        @DisplayName("Lanza ResourceNotFoundException si la orden del ítem no existe, sin tocar el ítem")
        void throwsNotFoundWhenOrderDoesNotExist() {
            UUID itemId = UUID.randomUUID();
            OrderItem item = buildItem(itemId, KitchenItemStatus.PENDING);

            when(orderItemRepository.findById(itemId)).thenReturn(Optional.of(item));
            when(orderRepository.findForCheckout(ORDER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> kitchenService.advanceItemStatus(itemId, KitchenItemStatus.IN_PREPARATION))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("La orden " + ORDER_ID + " no existe");

            assertThat(item.getKitchenStatus()).isEqualTo(KitchenItemStatus.PENDING);
            verify(orderItemRepository, never()).save(any());
            verify(eventPublisher, never()).publishEvent(any());
        }
    }

    @Nested
    @DisplayName("Promoción de la orden — lista solo cuando todos sus productos lo están")
    class OrderPromotionTests {

        @Test
        @DisplayName("Inválido: un producto listo y otro en preparación dejan la orden en SENT")
        void orderStaysSentWhileAnotherItemIsInPreparation() {
            UUID itemId1 = UUID.randomUUID();
            OrderItem item1 = buildItem(itemId1, KitchenItemStatus.IN_PREPARATION);
            OrderItem item2 = buildItem(UUID.randomUUID(), KitchenItemStatus.IN_PREPARATION);
            Order order = buildOrder(OrderStatus.SENT);

            when(orderItemRepository.findById(itemId1)).thenReturn(Optional.of(item1));
            when(orderRepository.findForCheckout(ORDER_ID)).thenReturn(Optional.of(order));
            when(orderItemRepository.findByOrderIdOrderByOrderedAt(ORDER_ID)).thenReturn(List.of(item1, item2));

            kitchenService.advanceItemStatus(itemId1, KitchenItemStatus.READY);

            assertThat(item1.getKitchenStatus()).isEqualTo(KitchenItemStatus.READY);
            assertThat(order.getStatus()).isEqualTo(OrderStatus.SENT);
            verify(orderRepository, never()).save(any());
        }

        @Test
        @DisplayName("Borde: un producto ya entregado cuenta como listo y la orden pasa a READY")
        void deliveredItemCountsAsReady() {
            UUID itemId1 = UUID.randomUUID();
            OrderItem item1 = buildItem(itemId1, KitchenItemStatus.IN_PREPARATION);
            OrderItem item2 = buildItem(UUID.randomUUID(), KitchenItemStatus.DELIVERED);
            Order order = buildOrder(OrderStatus.SENT);

            when(orderItemRepository.findById(itemId1)).thenReturn(Optional.of(item1));
            when(orderRepository.findForCheckout(ORDER_ID)).thenReturn(Optional.of(order));
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
            when(orderItemRepository.findByOrderIdOrderByOrderedAt(ORDER_ID)).thenReturn(List.of(item1, item2));

            kitchenService.advanceItemStatus(itemId1, KitchenItemStatus.READY);

            assertThat(order.getStatus()).isEqualTo(OrderStatus.READY);
            verify(orderRepository).save(order);
        }

        @Test
        @DisplayName("Borde: entregar el último producto de una orden ya READY no vuelve a guardar la orden")
        void orderAlreadyReadyIsNotSavedAgain() {
            UUID itemId = UUID.randomUUID();
            OrderItem item = buildItem(itemId, KitchenItemStatus.READY);
            Order order = buildOrder(OrderStatus.READY);

            when(orderItemRepository.findById(itemId)).thenReturn(Optional.of(item));
            when(orderRepository.findForCheckout(ORDER_ID)).thenReturn(Optional.of(order));
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
            when(orderItemRepository.findByOrderIdOrderByOrderedAt(ORDER_ID)).thenReturn(List.of(item));

            kitchenService.advanceItemStatus(itemId, KitchenItemStatus.DELIVERED);

            assertThat(order.getStatus()).isEqualTo(OrderStatus.READY);
            verify(orderRepository, never()).save(any());
        }
    }
}
