package com.callejon9.payment.web;

import com.callejon9.order.domain.Order;
import com.callejon9.order.repository.OrderRepository;
import com.callejon9.payment.service.MercadoPagoService;
import com.callejon9.payment.web.dto.CreatePreferenceRequest;
import com.callejon9.payment.web.dto.PaymentStatusResponse;
import com.callejon9.payment.web.dto.PreferenceResponse;
import com.callejon9.platform.tenant.domain.Tenant;
import com.callejon9.platform.tenant.repository.TenantRepository;
import com.callejon9.table.domain.RestaurantTable;
import com.callejon9.table.repository.RestaurantTableRepository;
import com.callejon9.tenancy.TenantContext;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("UT · MercadoPago Controllers")
class MercadoPagoControllerTest {

    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ORDER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID TABLE_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Mock
    private MercadoPagoService mercadoPagoService;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private RestaurantTableRepository tableRepository;

    @Mock
    private TransactionTemplate transactionTemplate;

    private MercadoPagoController mercadoPagoController;
    private MercadoPagoWebhookController webhookController;

    @BeforeEach
    void setUp() {
        TenantContext.set(TENANT_ID);
        lenient().when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });

        webhookController = new MercadoPagoWebhookController(mercadoPagoService);
        mercadoPagoController = new MercadoPagoController(
                mercadoPagoService,
                orderRepository,
                tenantRepository,
                tableRepository,
                transactionTemplate
        );
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Crea preferencia desde el endpoint de comanda autenticado")
    void createPreferenceEndpointSuccess() {
        Tenant tenant = Tenant.builder().name("Callejón 9").slug("callejon-9").build();
        tenant.setId(TENANT_ID);

        Order order = new Order();
        order.setId(ORDER_ID);
        order.setTableId(TABLE_ID);

        RestaurantTable table = RestaurantTable.builder().number(5).build();

        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(tableRepository.findById(TABLE_ID)).thenReturn(Optional.of(table));

        PreferenceResponse mockResp = new PreferenceResponse(
                "PREF-1", "https://mp.com/init", "https://mp.com/sandbox", "https://mp.com/qr",
                TENANT_ID + ":" + ORDER_ID, new BigDecimal("250.00"), "ORD-1", true
        );

        when(mercadoPagoService.createPreferenceForOrder(
                eq(TENANT_ID), eq(ORDER_ID), eq(new BigDecimal("10.0")), eq("Callejón 9"), eq("callejon-9"), eq(5)
        )).thenReturn(mockResp);

        CreatePreferenceRequest req = new CreatePreferenceRequest(new BigDecimal("10.0"));
        PreferenceResponse res = mercadoPagoController.createPreference(ORDER_ID, req);

        assertThat(res).isNotNull();
        assertThat(res.preferenceId()).isEqualTo("PREF-1");
        verify(mercadoPagoService).createPreferenceForOrder(
                eq(TENANT_ID), eq(ORDER_ID), eq(new BigDecimal("10.0")), eq("Callejón 9"), eq("callejon-9"), eq(5)
        );
    }

    @Test
    @DisplayName("Consulta estado de pago desde el endpoint autenticado")
    void getPaymentStatusEndpointSuccess() {
        PaymentStatusResponse mockStatus = new PaymentStatusResponse(
                ORDER_ID, "ORD-1", "PAID", true, "TCK-001", Instant.now()
        );
        when(mercadoPagoService.getPaymentStatus(TENANT_ID, ORDER_ID)).thenReturn(mockStatus);

        PaymentStatusResponse res = mercadoPagoController.getPaymentStatus(ORDER_ID);

        assertThat(res.paid()).isTrue();
        assertThat(res.ticketFolio()).isEqualTo("TCK-001");
    }

    @Test
    @DisplayName("Simula aprobación desde el endpoint autenticado")
    void simulateApprovalEndpointSuccess() {
        PaymentStatusResponse mockStatus = new PaymentStatusResponse(
                ORDER_ID, "ORD-1", "PAID", true, "TCK-SIM", Instant.now()
        );
        when(mercadoPagoService.simulateApproval(TENANT_ID, ORDER_ID, new BigDecimal("15.0"))).thenReturn(mockStatus);

        CreatePreferenceRequest req = new CreatePreferenceRequest(new BigDecimal("15.0"));
        PaymentStatusResponse res = mercadoPagoController.simulateApproval(ORDER_ID, req);

        assertThat(res.paid()).isTrue();
        assertThat(res.ticketFolio()).isEqualTo("TCK-SIM");
    }

    @Test
    @DisplayName("Webhook procesa notificación válida de Mercado Pago")
    void webhookProcessesNotification() {
        Map<String, Object> body = Map.of(
                "action", "payment.created",
                "data", Map.of("id", "998877"),
                "external_reference", TENANT_ID + ":" + ORDER_ID
        );

        when(mercadoPagoService.processWebhook(eq(body), any(), eq("ts=123,v1=abc"), eq("req-1"))).thenReturn(true);

        ResponseEntity<Map<String, Object>> response = webhookController.handleMercadoPagoWebhook(
                body,
                Map.of(),
                "ts=123,v1=abc",
                "req-1"
        );

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).containsEntry("status", "received");
        assertThat(response.getBody()).containsEntry("processed", true);
        verify(mercadoPagoService).processWebhook(eq(body), any(), eq("ts=123,v1=abc"), eq("req-1"));
    }

    @Test
    @DisplayName("Webhook maneja excepciones retornando 200 para evitar loops en Mercado Pago")
    void webhookHandlesExceptionGracefully() {
        Map<String, Object> body = Map.of("invalid", "payload");
        when(mercadoPagoService.processWebhook(any(), any(), any(), any())).thenThrow(new RuntimeException("Error simulado"));

        ResponseEntity<Map<String, Object>> response = webhookController.handleMercadoPagoWebhook(
                body,
                Map.of(),
                null,
                null
        );

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).containsEntry("status", "error");
    }

    @Test
    @DisplayName("Endpoint sync delega a MercadoPagoService con tenant autenticado")
    void syncPaymentDelegatesToService() {
        PaymentStatusResponse expected = new PaymentStatusResponse(ORDER_ID, "ORD-1", "PAID", true, "TCK-1", Instant.now());
        when(mercadoPagoService.syncPayment(TENANT_ID, ORDER_ID, "PAY-123", "ext-ref")).thenReturn(expected);

        PaymentStatusResponse result = mercadoPagoController.syncPayment(
                ORDER_ID,
                new com.callejon9.payment.web.dto.SyncPaymentRequest("PAY-123", "ext-ref")
        );

        assertThat(result.paid()).isTrue();
        assertThat(result.ticketFolio()).isEqualTo("TCK-1");
        verify(mercadoPagoService).syncPayment(TENANT_ID, ORDER_ID, "PAY-123", "ext-ref");
    }
}