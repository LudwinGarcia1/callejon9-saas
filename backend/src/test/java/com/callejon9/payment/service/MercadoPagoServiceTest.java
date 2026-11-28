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
import com.callejon9.shared.error.BusinessRuleException;
import com.callejon9.shared.error.ResourceNotFoundException;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
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
    @DisplayName("Rechaza crear preferencia si la orden ya está pagada")
    void createPreferenceThrowsWhenOrderAlreadyPaid() {
        Order order = Order.builder().folio("ORD-PAID").status(OrderStatus.PAID).build();
        order.setId(ORDER_ID);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.createPreferenceForOrder(TENANT_ID, ORDER_ID, BigDecimal.ZERO, "Demo", "demo", 1))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("ya está pagada");
    }

    @Test
    @DisplayName("Rechaza crear preferencia si la orden está cancelada")
    void createPreferenceThrowsWhenOrderCanceled() {
        Order order = Order.builder().folio("ORD-CANCEL").status(OrderStatus.CANCELED).build();
        order.setId(ORDER_ID);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.createPreferenceForOrder(TENANT_ID, ORDER_ID, BigDecimal.ZERO, "Demo", "demo", 1))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("está cancelada");
    }

    @Test
    @DisplayName("Rechaza crear preferencia si la orden no tiene productos")
    void createPreferenceThrowsWhenOrderHasNoItems() {
        Order order = Order.builder().folio("ORD-EMPTY").status(OrderStatus.NEW).build();
        order.setId(ORDER_ID);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(orderItemRepository.findByOrderIdOrderByOrderedAt(ORDER_ID)).thenReturn(List.of());

        assertThatThrownBy(() -> service.createPreferenceForOrder(TENANT_ID, ORDER_ID, BigDecimal.ZERO, "Demo", "demo", 1))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("no tiene productos");
    }

    @Test
    @DisplayName("Consulta de estado reporta no pagado cuando la orden sigue abierta")
    void getPaymentStatusReturnsUnpaid() {
        Order order = Order.builder().folio("ORD-0001").status(OrderStatus.SENT).build();
        order.setId(ORDER_ID);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

        PaymentStatusResponse status = service.getPaymentStatus(TENANT_ID, ORDER_ID);

        assertThat(status).isNotNull();
        assertThat(status.paid()).isFalse();
        assertThat(status.ticketFolio()).isNull();
    }

    @Test
    @DisplayName("Consulta de estado reporta pagado y con folio de ticket")
    void getPaymentStatusReturnsPaid() {
        Instant closedAt = Instant.now();
        Order order = Order.builder().folio("ORD-0001").status(OrderStatus.PAID).closedAt(closedAt).build();
        order.setId(ORDER_ID);
        Ticket ticket = Ticket.builder().folio("TCK-0099").build();

        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(ticketRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.of(ticket));

        PaymentStatusResponse status = service.getPaymentStatus(TENANT_ID, ORDER_ID);

        assertThat(status).isNotNull();
        assertThat(status.paid()).isTrue();
        assertThat(status.ticketFolio()).isEqualTo("TCK-0099");
        assertThat(status.closedAt()).isEqualTo(closedAt);
    }

    @Test
    @DisplayName("Procesa webhook exitoso con external_reference en body")
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
    @DisplayName("Procesa webhook ignorando notificaciones con external_reference inválido")
    void processWebhookIgnoresInvalidReference() {
        Map<String, Object> body = Map.of(
                "action", "payment.created",
                "external_reference", "invalido-sin-dos-puntos",
                "data", Map.of("id", "MP-PAY-123")
        );

        boolean processed = service.processWebhook(body, Map.of());

        assertThat(processed).isFalse();
        verify(checkoutService, never()).checkoutMercadoPago(any(), any(), any());
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

    @Test
    @DisplayName("Procesa webhook exitoso con tipPercent preservado en external_reference")
    void processWebhookWithTipSuccess() {
        String externalRef = TENANT_ID + ":" + ORDER_ID + ":15.0:123456789";
        Map<String, Object> body = Map.of(
                "action", "payment.created",
                "external_reference", externalRef,
                "data", Map.of("id", "MP-PAY-777")
        );

        boolean processed = service.processWebhook(body, Map.of());

        assertThat(processed).isTrue();
        verify(checkoutService).checkoutMercadoPago(eq(ORDER_ID), eq(new BigDecimal("15.0")), eq("MP-PAY-777"));
    }

    @Test
    @DisplayName("En producción rechaza webhook si no incluye paymentId y se intenta forjar body")
    void processWebhookInProductionRejectsForgedBody() {
        properties.setMockEnabled(false);
        properties.setAccessToken("TEST-ACCESS-TOKEN");

        Map<String, Object> body = Map.of(
                "action", "payment.created",
                "external_reference", TENANT_ID + ":" + ORDER_ID + ":0:123"
        );

        boolean processed = service.processWebhook(body, Map.of());

        assertThat(processed).isFalse();
        verify(checkoutService, never()).checkoutMercadoPago(any(), any(), any());
    }

    @Test
    @DisplayName("En producción consulta la API de Mercado Pago y extrae la referencia verificada")
    void processWebhookInProductionQueriesApi() throws Exception {
        properties.setMockEnabled(false);
        properties.setAccessToken("TEST-ACCESS-TOKEN");

        String externalRef = TENANT_ID + ":" + ORDER_ID + ":10.0:123456789";
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        com.fasterxml.jackson.databind.JsonNode details = mapper.readTree("""
                {
                    "id": 888999,
                    "status": "approved",
                    "external_reference": "%s",
                    "transaction_amount": 250.00
                }
                """.formatted(externalRef));

        when(client.getPaymentDetails("888999")).thenReturn(details);

        Map<String, Object> body = Map.of(
                "action", "payment.created",
                "data", Map.of("id", "888999")
        );

        boolean processed = service.processWebhook(body, Map.of());

        assertThat(processed).isTrue();
        verify(checkoutService).checkoutMercadoPago(eq(ORDER_ID), eq(new BigDecimal("10.0")), eq("888999"));
    }

    @Test
    @DisplayName("Valida firma HMAC-SHA256 cuando webhookSecret está configurado")
    void processWebhookSignatureValidation() {
        properties.setWebhookSecret("super-secret-key");

        // Con firma inválida debe rechazar
        boolean invalid = service.processWebhook(
                Map.of("data", Map.of("id", "123")),
                Map.of(),
                "ts=1700000000,v1=invalido",
                "req-1"
        );
        assertThat(invalid).isFalse();

        // Con firma válida
        String paymentId = "123";
        String requestId = "req-1";
        String ts = "1700000000";
        String manifest = "id:" + paymentId + ";request-id:" + requestId + ";ts:" + ts + ";";

        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec("super-secret-key".getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hmacBytes = mac.doFinal(manifest.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hmacBytes) sb.append(String.format("%02x", b));
            String validSignature = "ts=" + ts + ",v1=" + sb.toString();

            Map<String, Object> body = Map.of(
                    "data", Map.of("id", paymentId),
                    "external_reference", TENANT_ID + ":" + ORDER_ID + ":0:999"
            );

            boolean valid = service.processWebhook(body, Map.of(), validSignature, requestId);
            assertThat(valid).isTrue();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("syncPayment retorna pagado si la orden ya estaba cerrada")
    void syncPaymentAlreadyPaid() {
        Order order = Order.builder().folio("ORD-0001").status(OrderStatus.PAID).build();
        order.setId(ORDER_ID);
        Ticket ticket = Ticket.builder().folio("TCK-0001").build();

        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(ticketRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.of(ticket));

        PaymentStatusResponse res = service.syncPayment(TENANT_ID, ORDER_ID, "PAY-1", "ref");

        assertThat(res.paid()).isTrue();
        assertThat(res.ticketFolio()).isEqualTo("TCK-0001");
        verify(checkoutService, never()).checkoutMercadoPago(any(), any(), any());
    }

    @Test
    @DisplayName("syncPayment en modo real consulta pago aprobado y ejecuta checkout")
    void syncPaymentRealApproved() throws Exception {
        properties.setMockEnabled(false);
        properties.setAccessToken("test-token");

        Order order = Order.builder().folio("ORD-0001").status(OrderStatus.READY).build();
        order.setId(ORDER_ID);

        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        String json = """
        {
            "id": 987654321,
            "status": "approved",
            "external_reference": "%s:%s:10:123456"
        }
        """.formatted(TENANT_ID, ORDER_ID);
        com.fasterxml.jackson.databind.JsonNode paymentNode = mapper.readTree(json);

        when(client.findPaymentByExternalReference("ref-123")).thenReturn(paymentNode);

        Ticket ticket = Ticket.builder().folio("TCK-9988").build();
        when(checkoutService.checkoutMercadoPago(eq(ORDER_ID), eq(new BigDecimal("10")), eq("987654321"))).thenReturn(ticket);

        PaymentStatusResponse res = service.syncPayment(TENANT_ID, ORDER_ID, null, "ref-123");

        assertThat(res.paid()).isTrue();
        assertThat(res.ticketFolio()).isEqualTo("TCK-9988");
        verify(checkoutService).checkoutMercadoPago(eq(ORDER_ID), eq(new BigDecimal("10")), eq("987654321"));
    }
}