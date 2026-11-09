package com.callejon9.payment.web;

import com.callejon9.payment.service.MercadoPagoService;
import com.callejon9.payment.web.dto.CreatePreferenceRequest;
import com.callejon9.payment.web.dto.PaymentStatusResponse;
import com.callejon9.payment.web.dto.PreferenceResponse;
import com.callejon9.tenancy.TenantContext;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("UT · MercadoPago Controllers")
class MercadoPagoControllerTest {

    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ORDER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Mock
    private MercadoPagoService mercadoPagoService;

    private MercadoPagoWebhookController webhookController;

    @BeforeEach
    void setUp() {
        TenantContext.set(TENANT_ID);
        webhookController = new MercadoPagoWebhookController(mercadoPagoService);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Webhook procesa notificación válida de Mercado Pago")
    void webhookProcessesNotification() {
        Map<String, Object> body = Map.of(
                "action", "payment.created",
                "data", Map.of("id", "998877"),
                "external_reference", TENANT_ID + ":" + ORDER_ID
        );

        when(mercadoPagoService.processWebhook(eq(body), any())).thenReturn(true);

        ResponseEntity<Map<String, Object>> response = webhookController.handleMercadoPagoWebhook(
                body,
                Map.of(),
                "ts=123,v1=abc",
                "req-1"
        );

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).containsEntry("status", "received");
        assertThat(response.getBody()).containsEntry("processed", true);
        verify(mercadoPagoService).processWebhook(eq(body), any());
    }

    @Test
    @DisplayName("Webhook maneja excepciones retornando 200 para evitar loops en Mercado Pago")
    void webhookHandlesExceptionGracefully() {
        Map<String, Object> body = Map.of("invalid", "payload");
        when(mercadoPagoService.processWebhook(any(), any())).thenThrow(new RuntimeException("Error simulado"));

        ResponseEntity<Map<String, Object>> response = webhookController.handleMercadoPagoWebhook(
                body,
                Map.of(),
                null,
                null
        );

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).containsEntry("status", "error");
    }
}
