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
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
    }
}
