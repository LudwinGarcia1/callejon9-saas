package com.callejon9.payment.web;

import com.callejon9.payment.service.MercadoPagoService;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/webhooks")
public class MercadoPagoWebhookController {

    private static final Logger log = LoggerFactory.getLogger(MercadoPagoWebhookController.class);

    private final MercadoPagoService mercadoPagoService;

    public MercadoPagoWebhookController(MercadoPagoService mercadoPagoService) {
        this.mercadoPagoService = mercadoPagoService;
    }

    @PostMapping("/mercadopago")
    public ResponseEntity<Map<String, Object>> handleMercadoPagoWebhook(
            @RequestBody(required = false) Map<String, Object> body,
            @RequestParam(required = false) Map<String, String> queryParams,
            @RequestHeader(value = "x-signature", required = false) String signature,
            @RequestHeader(value = "x-request-id", required = false) String requestId) {

        log.info("Webhook recibido de Mercado Pago: body={} params={} requestId={}", body, queryParams, requestId);

        try {
            boolean processed = mercadoPagoService.processWebhook(body, queryParams);
            return ResponseEntity.ok(Map.of("status", "received", "processed", processed));
        } catch (Exception e) {
            log.error("Error al procesar webhook de Mercado Pago: {}", e.getMessage(), e);
            // Retornamos 200 para evitar que Mercado Pago reintente en bucle en caso de payloads malformados
            return ResponseEntity.ok(Map.of("status", "error", "message", e.getMessage()));
        }
    }
}
