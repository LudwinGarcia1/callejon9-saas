package com.callejon9.payment.service;

import com.callejon9.order.domain.Order;
import com.callejon9.order.domain.OrderItem;
import com.callejon9.order.domain.OrderStatus;
import com.callejon9.order.repository.OrderItemRepository;
import com.callejon9.order.repository.OrderRepository;
import com.callejon9.payment.config.MercadoPagoProperties;
import com.callejon9.payment.web.dto.PaymentStatusResponse;
import com.callejon9.payment.web.dto.PreferenceResponse;
import com.callejon9.platform.tenant.repository.TenantRepository;
import com.callejon9.sale.domain.PaymentMethod;
import com.callejon9.sale.service.CheckoutService;
import com.callejon9.table.repository.RestaurantTableRepository;
import com.callejon9.ticket.domain.Ticket;
import com.callejon9.ticket.repository.TicketRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("UT · MercadoPagoService")
class MercadoPagoServiceTest {

    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ORDER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Mock
    private MercadoPagoClient client;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private RestaurantTableRepository tableRepository;

    @Mock
    private CheckoutService checkoutService;

    @Mock
    private TransactionTemplate transactionTemplate;

    private MercadoPagoProperties properties;
    private MercadoPagoService service;

    @BeforeEach
    void setUp() {
        properties = new MercadoPagoProperties();
        properties.setMockEnabled(true);
        properties.setFrontendBaseUrl("http://localhost:3000");

        lenient().when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });

        service = new MercadoPagoService(
                client,
                properties,
                orderRepository,
                orderItemRepository,
                ticketRepository,
                tenantRepository,
                tableRepository,
                checkoutService,
                transactionTemplate
        );
    }

    @Test
    @DisplayName("Crea preferencia de pago para una orden activa con propina")
    void createPreferenceSuccess() {
        Order order = Order.builder()
                .folio("ORD-0001")
                .status(OrderStatus.SENT)
                .build();
        order.setId(ORDER_ID);

        OrderItem item = OrderItem.builder()
                .orderId(ORDER_ID)
                .productName("Taco al Pastor")
                .unitPrice(new BigDecimal("50.00"))
                .quantity(2)
                .build();

        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(orderItemRepository.findByOrderIdOrderByOrderedAt(ORDER_ID)).thenReturn(List.of(item));

        PreferenceResponse expectedResp = new PreferenceResponse(
                "PREF-123",
                "https://mercadopago.com/checkout/123",
                "https://mercadopago.com/checkout/123",
                "https://mercadopago.com/checkout/123",
                TENANT_ID + ":" + ORDER_ID,
                new BigDecimal("110.00"),
                "ORD-0001",
                true
        );

        when(client.createPreference(eq("RestoHub Demo"), eq("ORD-0001"), eq(new BigDecimal("110.00")), any(), any()))
                .thenReturn(expectedResp);

        PreferenceResponse response = service.createPreferenceForOrder(
                TENANT_ID,
                ORDER_ID,
                new BigDecimal("10.0"), // 10% propina sobre $100 = $10 -> Total $110
                "RestoHub Demo",
                "restohub-demo",
                3
        );

        assertThat(response).isNotNull();
        assertThat(response.preferenceId()).isEqualTo("PREF-123");
        assertThat(response.total()).isEqualTo(new BigDecimal("110.00"));
    }

    @Test
    @DisplayName("Procesa webhook exitoso con external_reference")
    void processWebhookSuccess() {
        String externalRef = TENANT_ID + ":" + ORDER_ID + ":123456789";
        Map<String, Object> body = Map.of(
                "action", "payment.created",
                "external_reference", externalRef,
                "data", Map.of("id", "MP-PAY-999")
        );

        boolean processed = service.processWebhook(body, Map.of());

        assertThat(processed).isTrue();
        verify(checkoutService).checkoutMercadoPago(eq(ORDER_ID), eq(BigDecimal.ZERO), eq("MP-PAY-999"));
    }

    @Test
    @DisplayName("Simula aprobación de cobro en modo mock/demo")
    void simulateApprovalSuccess() {
        Order order = Order.builder()
                .folio("ORD-0002")
                .status(OrderStatus.PAID)
                .build();
        order.setId(ORDER_ID);

        Ticket ticket = Ticket.builder()
                .folio("TCK-0005")
                .paymentMethod(PaymentMethod.MERCADOPAGO)
                .closedAt(Instant.now())
                .build();

        when(checkoutService.checkoutMercadoPago(eq(ORDER_ID), eq(new BigDecimal("10.0")), any()))
                .thenReturn(ticket);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

        PaymentStatusResponse status = service.simulateApproval(TENANT_ID, ORDER_ID, new BigDecimal("10.0"));

        assertThat(status).isNotNull();
        assertThat(status.paid()).isTrue();
        assertThat(status.ticketFolio()).isEqualTo("TCK-0005");
    }
}
