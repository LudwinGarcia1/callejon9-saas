package com.callejon9.payment.service;

import com.callejon9.payment.config.MercadoPagoProperties;
import com.callejon9.payment.web.dto.PreferenceResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class MercadoPagoClient {

    private static final Logger log = LoggerFactory.getLogger(MercadoPagoClient.class);

    private final MercadoPagoProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public MercadoPagoClient(MercadoPagoProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder().build();
    }

    public PreferenceResponse createPreference(
            String restaurantName,
            String folio,
            BigDecimal total,
            String externalReference,
            String successBackUrl) {

        if (properties.isMockEnabled()) {
            String mockId = "MOCK-PREF-" + UUID.randomUUID();
            String mockUrl = properties.getFrontendBaseUrl()
                    + "/payment/mercadopago/mock?pref_id=" + mockId
                    + "&ref=" + URLEncoder.encode(externalReference, StandardCharsets.UTF_8)
                    + "&total=" + total
                    + "&folio=" + URLEncoder.encode(folio, StandardCharsets.UTF_8);

            log.info("Mercado Pago MOCK habilitado: preferencia simulada id={} total={} ref={}",
                    mockId, total, externalReference);

            return new PreferenceResponse(
                    mockId,
                    mockUrl,
                    mockUrl,
                    mockUrl,
                    externalReference,
                    total,
                    folio,
                    true
            );
        }

        try {
            Map<String, Object> item = Map.of(
                    "id", folio,
                    "title", "Consumo en " + restaurantName + " (" + folio + ")",
                    "description", "Orden de consumo RestoHub",
                    "quantity", 1,
                    "unit_price", total,
                    "currency_id", "MXN"
            );

            Map<String, String> backUrls = Map.of(
                    "success", successBackUrl,
                    "failure", successBackUrl + "?payment=failure",
                    "pending", successBackUrl + "?payment=pending"
            );

            String notificationUrl = properties.getBackendBaseUrl() + "/api/v1/webhooks/mercadopago";

            Map<String, Object> requestPayload = Map.of(
                    "items", List.of(item),
                    "external_reference", externalReference,
                    "back_urls", backUrls,
                    "auto_return", "approved",
                    "notification_url", notificationUrl,
                    "statement_descriptor", "RESTOHUB"
            );

            String responseBody = restClient.post()
                    .uri(properties.getBaseUrl() + "/checkout/preferences")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getAccessToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestPayload)
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(responseBody);
            String id = root.path("id").asText();
            String initPoint = root.path("init_point").asText();
            String sandboxInitPoint = root.path("sandbox_init_point").asText(initPoint);

            return new PreferenceResponse(
                    id,
                    initPoint,
                    sandboxInitPoint,
                    initPoint,
                    externalReference,
                    total,
                    folio,
                    false
            );
        } catch (Exception e) {
            log.error("Error al crear preferencia en Mercado Pago: {}", e.getMessage(), e);
            throw new IllegalStateException("No se pudo conectar con Mercado Pago: " + e.getMessage(), e);
        }
    }

    public JsonNode getPaymentDetails(String paymentId) {
        if (properties.isMockEnabled()) {
            return null;
        }

        try {
            String response = restClient.get()
                    .uri(properties.getBaseUrl() + "/v1/payments/" + paymentId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getAccessToken())
                    .retrieve()
                    .body(String.class);

            return objectMapper.readTree(response);
        } catch (Exception e) {
            log.error("Error al consultar pago {} en Mercado Pago: {}", paymentId, e.getMessage());
            return null;
        }
    }
}
