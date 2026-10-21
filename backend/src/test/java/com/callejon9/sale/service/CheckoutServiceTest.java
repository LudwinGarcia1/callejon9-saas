package com.callejon9.sale.service;

import com.callejon9.order.domain.Order;
import com.callejon9.order.domain.OrderItem;
import com.callejon9.order.domain.OrderStatus;
import com.callejon9.order.repository.OrderItemRepository;
import com.callejon9.order.repository.OrderRepository;
import com.callejon9.order.service.FolioGenerator;
import com.callejon9.sale.domain.Payment;
import com.callejon9.sale.domain.PaymentMethod;
import com.callejon9.sale.domain.Sale;
import com.callejon9.sale.repository.PaymentRepository;
import com.callejon9.sale.repository.SaleRepository;
import com.callejon9.sale.web.dto.CheckoutRequest;
import com.callejon9.sale.web.dto.PaymentRequest;
import com.callejon9.shared.error.BusinessRuleException;
import com.callejon9.shared.error.ResourceNotFoundException;
import com.callejon9.table.service.TableService;
import com.callejon9.tenancy.TenantContext;
import com.callejon9.ticket.domain.Ticket;
import com.callejon9.ticket.repository.TicketRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("UT · CheckoutService (Cobro y Pagos)")
class CheckoutServiceTest {

    private static final UUID TENANT_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID CASHIER_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID TABLE_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private SaleRepository saleRepository;

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private TableService tableService;

    @Mock
    private FolioGenerator folioGenerator;

    @Mock
    private PaymentRepository paymentRepository;

    private CheckoutService checkoutService;

    @BeforeEach
    void setUp() {
        TenantContext.set(TENANT_ID);
        checkoutService = new CheckoutService(
                orderRepository,
                orderItemRepository,
                saleRepository,
                ticketRepository,
                tableService,
                folioGenerator,
                paymentRepository
        );
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private Order buildActiveOrder(UUID orderId) {
        Order order = new Order();
        order.setId(orderId);
        order.setTenantId(TENANT_ID);
        order.setTableId(TABLE_ID);
        order.setFolio("ORD-2026-001");
        order.setStatus(OrderStatus.READY);
        return order;
    }

    private OrderItem buildOrderItem(UUID orderId, String name, BigDecimal price, int quantity) {
        OrderItem item = new OrderItem();
        item.setId(UUID.randomUUID());
        item.setOrderId(orderId);
        item.setProductId(UUID.randomUUID());
        item.setProductName(name);
        item.setUnitPrice(price);
        item.setQuantity(quantity);
        item.setOrderedAt(Instant.now());
        return item;
    }

    @Nested
    @DisplayName("Validaciones de entrada y estados")
    class InputAndStateValidations {

        @Test
        @DisplayName("Lanza ResourceNotFoundException si la orden no existe")
        void throwsNotFoundWhenOrderMissing() {
            UUID orderId = UUID.randomUUID();
            when(orderRepository.findForCheckout(orderId)).thenReturn(Optional.empty());

            CheckoutRequest request = new CheckoutRequest(PaymentMethod.CASH, BigDecimal.ZERO, null);

            assertThatThrownBy(() -> checkoutService.checkout(orderId, request, CASHIER_ID))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("no existe");
        }

        @Test
        @DisplayName("Lanza BusinessRuleException si la orden ya fue pagada")
        void throwsBusinessRuleWhenOrderAlreadyPaid() {
            UUID orderId = UUID.randomUUID();
            Order order = buildActiveOrder(orderId);
            order.setStatus(OrderStatus.PAID);

            when(orderRepository.findForCheckout(orderId)).thenReturn(Optional.of(order));

            CheckoutRequest request = new CheckoutRequest(PaymentMethod.CASH, BigDecimal.ZERO, null);

            assertThatThrownBy(() -> checkoutService.checkout(orderId, request, CASHIER_ID))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("ya esta PAID");
        }

        @Test
        @DisplayName("Lanza BusinessRuleException si la orden no tiene partidas")
        void throwsBusinessRuleWhenOrderHasNoItems() {
            UUID orderId = UUID.randomUUID();
            Order order = buildActiveOrder(orderId);

            when(orderRepository.findForCheckout(orderId)).thenReturn(Optional.of(order));
            when(orderItemRepository.findByOrderIdOrderByOrderedAt(orderId)).thenReturn(List.of());

            CheckoutRequest request = new CheckoutRequest(PaymentMethod.CASH, BigDecimal.ZERO, null);

            assertThatThrownBy(() -> checkoutService.checkout(orderId, request, CASHIER_ID))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("no tiene productos");
        }

        @Test
        @DisplayName("Rechaza petición que envía payments y paymentMethod a la vez")
        void throwsInvalidPaymentWhenBothMethodsProvided() {
            UUID orderId = UUID.randomUUID();
            Order order = buildActiveOrder(orderId);
            OrderItem item = buildOrderItem(orderId, "Tacos", new BigDecimal("100.00"), 1);

            when(orderRepository.findForCheckout(orderId)).thenReturn(Optional.of(order));
            when(orderItemRepository.findByOrderIdOrderByOrderedAt(orderId)).thenReturn(List.of(item));

            List<PaymentRequest> payments = List.of(new PaymentRequest(PaymentMethod.CASH, new BigDecimal("100.00")));
            CheckoutRequest request = new CheckoutRequest(PaymentMethod.CASH, BigDecimal.ZERO, payments);

            assertThatThrownBy(() -> checkoutService.checkout(orderId, request, CASHIER_ID))
                    .isInstanceOf(InvalidPaymentException.class)
                    .hasMessageContaining("no ambos");
        }

        @Test
        @DisplayName("Rechaza con InvalidPaymentException cuando los pagos no cubren el total")
        void throwsInvalidPaymentWhenPaymentsDoNotCoverTotal() {
            UUID orderId = UUID.randomUUID();
            Order order = buildActiveOrder(orderId);
            OrderItem item = buildOrderItem(orderId, "Corte Ribeye", new BigDecimal("450.00"), 1);

            when(orderRepository.findForCheckout(orderId)).thenReturn(Optional.of(order));
            when(orderItemRepository.findByOrderIdOrderByOrderedAt(orderId)).thenReturn(List.of(item));

            // Subtotal $450, total $450, pero solo se envían $300
            List<PaymentRequest> payments = List.of(new PaymentRequest(PaymentMethod.CASH, new BigDecimal("300.00")));
            CheckoutRequest request = new CheckoutRequest(null, BigDecimal.ZERO, payments);

            assertThatThrownBy(() -> checkoutService.checkout(orderId, request, CASHIER_ID))
                    .isInstanceOf(InvalidPaymentException.class);
        }
    }

    @Nested
    @DisplayName("Cálculos de totales, propinas y cambio")
    class TotalCalculationAndChange {

        @Test
        @DisplayName("Calcula propina porcentual con redondeo HALF_UP y entrega cambio en efectivo")
        void calculatesTipAndCashChangeCorrectly() {
            UUID orderId = UUID.randomUUID();
            Order order = buildActiveOrder(orderId);
            // 3 x $66.66 = $199.98
            OrderItem item = buildOrderItem(orderId, "Menú Ejecutivo", new BigDecimal("66.66"), 3);

            when(orderRepository.findForCheckout(orderId)).thenReturn(Optional.of(order));
            when(orderItemRepository.findByOrderIdOrderByOrderedAt(orderId)).thenReturn(List.of(item));
            when(folioGenerator.next("TCK-")).thenReturn("TCK-2026-999");
            when(saleRepository.save(any(Sale.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(ticketRepository.save(any(Ticket.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // 10% de propina sobre $199.98 = $19.998 -> redondeado a $20.00
            // Total = $199.98 + $20.00 = $219.98
            // Cliente paga en efectivo con $300.00 -> Cambio = $80.02
            List<PaymentRequest> payments = List.of(new PaymentRequest(PaymentMethod.CASH, new BigDecimal("300.00")));
            CheckoutRequest request = new CheckoutRequest(null, new BigDecimal("10.00"), payments);

            Ticket ticket = checkoutService.checkout(orderId, request, CASHIER_ID);

            assertThat(ticket).isNotNull();
            assertThat(ticket.getSubtotal()).isEqualByComparingTo("199.98");
            assertThat(ticket.getTip()).isEqualByComparingTo("20.00");
            assertThat(ticket.getTotal()).isEqualByComparingTo("219.98");
            assertThat(ticket.getChange()).isEqualByComparingTo("80.02");

            // Verifica que la mesa se libera y la orden se marca PAID
            verify(tableService).free(TABLE_ID);
            assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        }
    }
}
