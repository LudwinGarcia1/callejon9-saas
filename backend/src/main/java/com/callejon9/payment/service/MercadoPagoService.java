package com.callejon9.payment.service;

import com.callejon9.catalog.domain.Product;
import com.callejon9.catalog.repository.ProductRepository;
import com.callejon9.order.domain.Order;
import com.callejon9.order.domain.OrderItem;
import com.callejon9.order.domain.OrderStatus;
import com.callejon9.order.repository.OrderItemRepository;
import com.callejon9.order.repository.OrderRepository;
import com.callejon9.payment.config.MercadoPagoProperties;
import com.callejon9.payment.web.dto.PaymentStatusResponse;
import com.callejon9.payment.web.dto.PreferenceResponse;
import com.callejon9.platform.tenant.domain.Tenant;
import com.callejon9.platform.tenant.repository.TenantRepository;
import com.callejon9.sale.service.CheckoutService;
import com.callejon9.shared.error.BusinessRuleException;
import com.callejon9.shared.error.ResourceNotFoundException;
import com.callejon9.table.domain.RestaurantTable;
import com.callejon9.table.repository.RestaurantTableRepository;
import com.callejon9.tenancy.TenantContext;
import com.callejon9.ticket.domain.Ticket;
import com.callejon9.ticket.repository.TicketRepository;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class MercadoPagoService {

    private static final Logger log = LoggerFactory.getLogger(MercadoPagoService.class);

    private final MercadoPagoClient client;
    private final MercadoPagoProperties properties;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final TicketRepository ticketRepository;
    private final TenantRepository tenantRepository;
    private final RestaurantTableRepository tableRepository;
    private final CheckoutService checkoutService;
    private final TransactionTemplate transactionTemplate;

    public MercadoPagoService(
            MercadoPagoClient client,
            MercadoPagoProperties properties,
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            TicketRepository ticketRepository,
            TenantRepository tenantRepository,
            RestaurantTableRepository tableRepository,
            CheckoutService checkoutService,
            TransactionTemplate transactionTemplate) {
        this.client = client;
        this.properties = properties;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.ticketRepository = ticketRepository;
        this.tenantRepository = tenantRepository;
        this.tableRepository = tableRepository;
        this.checkoutService = checkoutService;
        this.transactionTemplate = transactionTemplate;
    }

    public PreferenceResponse createPreferenceForOrder(
            UUID tenantId,
            UUID orderId,
            BigDecimal tipPercent,
            String restaurantName,
            String restaurantSlug,
            Integer tableNumber) {

        return TenantContext.callAs(tenantId, () -> transactionTemplate.execute(status -> {
            Order order = orderRepository.findById(orderId)
                    .orElseThrow(() -> new ResourceNotFoundException("La orden " + orderId + " no existe."));

            if (order.getStatus() == OrderStatus.PAID) {
                throw new BusinessRuleException("La orden " + order.getFolio() + " ya está pagada.");
            }
            if (order.getStatus() == OrderStatus.CANCELED) {
                throw new BusinessRuleException("La orden " + order.getFolio() + " está cancelada.");
            }

            List<OrderItem> items = orderItemRepository.findByOrderIdOrderByOrderedAt(orderId);
            if (items.isEmpty()) {
                throw new BusinessRuleException("La orden no tiene productos registrados.");
            }

            BigDecimal effectiveTipPercent = tipPercent != null ? tipPercent : BigDecimal.ZERO;
            BigDecimal subtotal = items.stream()
                    .map(it -> it.getUnitPrice().multiply(BigDecimal.valueOf(it.getQuantity())))
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .setScale(2, RoundingMode.HALF_UP);
            BigDecimal tip = subtotal.multiply(effectiveTipPercent)
                    .divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP)
                    .setScale(2, RoundingMode.HALF_UP);
            BigDecimal total = subtotal.add(tip).setScale(2, RoundingMode.HALF_UP);

            String externalReference = tenantId + ":" + orderId + ":"
                    + effectiveTipPercent.stripTrailingZeros().toPlainString() + ":"
                    + System.currentTimeMillis();

            String successBackUrl;
            if (tableNumber != null && restaurantSlug != null) {
                successBackUrl = properties.getFrontendBaseUrl() + "/m/" + restaurantSlug + "/" + tableNumber;
            } else {
                successBackUrl = properties.getFrontendBaseUrl() + "/cashier";
            }

            return client.createPreference(
                    restaurantName != null ? restaurantName : "RestoHub",
                    order.getFolio(),
                    total,
                    externalReference,
                    successBackUrl
            );
        }));
    }

    public PaymentStatusResponse getPaymentStatus(UUID tenantId, UUID orderId) {
        return TenantContext.callAs(tenantId, () -> transactionTemplate.execute(status -> {
            Order order = orderRepository.findById(orderId)
                    .orElseThrow(() -> new ResourceNotFoundException("La orden " + orderId + " no existe."));

            boolean isPaid = order.getStatus() == OrderStatus.PAID;
            String ticketFolio = null;
            if (isPaid) {
                ticketFolio = ticketRepository.findByOrderId(orderId)
                        .map(Ticket::getFolio)
                        .orElse(null);
            }

            return new PaymentStatusResponse(
                    order.getId(),
                    order.getFolio(),
                    order.getStatus().name(),
                    isPaid,
                    ticketFolio,
                    order.getClosedAt()
            );
        }));
    }

    public PaymentStatusResponse syncPayment(UUID tenantId, UUID orderId, String paymentId, String externalReference) {
        return TenantContext.callAs(tenantId, () -> transactionTemplate.execute(status -> {
            Order order = orderRepository.findById(orderId)
                    .orElseThrow(() -> new ResourceNotFoundException("La orden " + orderId + " no existe."));

            if (order.getStatus() == OrderStatus.PAID) {
                String ticketFolio = ticketRepository.findByOrderId(orderId)
                        .map(Ticket::getFolio)
                        .orElse(null);
                return new PaymentStatusResponse(
                        order.getId(),
                        order.getFolio(),
                        order.getStatus().name(),
                        true,
                        ticketFolio,
                        order.getClosedAt()
                );
            }

            if (properties.isMockEnabled()) {
                return new PaymentStatusResponse(
                        order.getId(),
                        order.getFolio(),
                        order.getStatus().name(),
                        false,
                        null,
                        order.getClosedAt()
                );
            }

            JsonNode paymentDetails = null;
            if (paymentId != null && !paymentId.isBlank()) {
                paymentDetails = client.getPaymentDetails(paymentId);
            } else if (externalReference != null && !externalReference.isBlank()) {
                paymentDetails = client.findPaymentByExternalReference(externalReference);
            }

            if (paymentDetails == null) {
                return new PaymentStatusResponse(
                        order.getId(),
                        order.getFolio(),
                        order.getStatus().name(),
                        false,
                        null,
                        order.getClosedAt()
                );
            }

            String paymentStatus = paymentDetails.path("status").asText();
            if (!"approved".equalsIgnoreCase(paymentStatus)) {
                return new PaymentStatusResponse(
                        order.getId(),
                        order.getFolio(),
                        order.getStatus().name(),
                        false,
                        null,
                        order.getClosedAt()
                );
            }

            String officialRef = paymentDetails.path("external_reference").asText(null);
            if (officialRef == null || !officialRef.contains(":")) {
                log.warn("Pago de Mercado Pago {} sin external_reference valido: {}", paymentId, officialRef);
                return new PaymentStatusResponse(
                        order.getId(),
                        order.getFolio(),
                        order.getStatus().name(),
                        false,
                        null,
                        order.getClosedAt()
                );
            }

            String[] parts = officialRef.split(":");
            if (parts.length < 2) {
                log.warn("external_reference con formato invalido en sincronizacion: {}", officialRef);
                return new PaymentStatusResponse(
                        order.getId(),
                        order.getFolio(),
                        order.getStatus().name(),
                        false,
                        null,
                        order.getClosedAt()
                );
            }

            try {
                UUID refTenantId = UUID.fromString(parts[0]);
                UUID refOrderId = UUID.fromString(parts[1]);

                if (!refTenantId.equals(tenantId) || !refOrderId.equals(orderId)) {
                    log.warn("Discrepancia en sincro de pago: refTenant={}, refOrder={}, currentTenant={}, currentOrder={}",
                            refTenantId, refOrderId, tenantId, orderId);
                    return new PaymentStatusResponse(
                            order.getId(),
                            order.getFolio(),
                            order.getStatus().name(),
                            false,
                            null,
                            order.getClosedAt()
                    );
                }

                BigDecimal tipPercent = BigDecimal.ZERO;
                if (parts.length >= 4) {
                    try {
                        tipPercent = new BigDecimal(parts[2]);
                    } catch (Exception ignored) {
                    }
                }

                String actualPaymentId = paymentDetails.path("id").asText(paymentId != null ? paymentId : "MP-SYNC");
                Ticket ticket = checkoutService.checkoutMercadoPago(orderId, tipPercent, actualPaymentId);
                Order updatedOrder = orderRepository.findById(orderId).orElse(order);

                return new PaymentStatusResponse(
                        updatedOrder.getId(),
                        updatedOrder.getFolio(),
                        updatedOrder.getStatus().name(),
                        true,
                        ticket.getFolio(),
                        updatedOrder.getClosedAt()
                );
            } catch (Exception e) {
                log.error("Error al sincronizar pago con Mercado Pago para orden {}: {}", orderId, e.getMessage(), e);
                return new PaymentStatusResponse(
                        order.getId(),
                        order.getFolio(),
                        order.getStatus().name(),
                        false,
                        null,
                        order.getClosedAt()
                );
            }
        }));
    }

    public boolean isValidSignature(String paymentId, String requestId, String signatureHeader) {
        String secret = properties.getWebhookSecret();
        if (secret == null || secret.isBlank()) {
            return true;
        }
        if (signatureHeader == null || signatureHeader.isBlank()) {
            log.warn("Webhook de Mercado Pago rechazado: encabezado x-signature ausente");
            return false;
        }

        String ts = null;
        String hash = null;
        for (String part : signatureHeader.split(",")) {
            String[] kv = part.trim().split("=", 2);
            if (kv.length == 2) {
                if ("ts".equalsIgnoreCase(kv[0])) {
                    ts = kv[1];
                } else if ("v1".equalsIgnoreCase(kv[0])) {
                    hash = kv[1];
                }
            }
        }

        if (ts == null || hash == null) {
            log.warn("Webhook de Mercado Pago rechazado: x-signature con formato invalido ({})", signatureHeader);
            return false;
        }

        try {
            String manifest = "id:" + (paymentId != null ? paymentId : "")
                    + ";request-id:" + (requestId != null ? requestId : "")
                    + ";ts:" + ts + ";";

            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hmacBytes = mac.doFinal(manifest.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hmacBytes) {
                sb.append(String.format("%02x", b));
            }
            String calculatedHash = sb.toString();

            boolean matches = MessageDigest.isEqual(
                    calculatedHash.getBytes(StandardCharsets.UTF_8),
                    hash.getBytes(StandardCharsets.UTF_8)
            );
            if (!matches) {
                log.warn("Firma de webhook Mercado Pago no coincide. Recibida: {}, Calculada: {}", hash, calculatedHash);
            }
            return matches;
        } catch (Exception e) {
            log.error("Error al calcular firma HMAC-SHA256 de Mercado Pago: {}", e.getMessage(), e);
            return false;
        }
    }

    public boolean processWebhook(Map<String, Object> body, Map<String, String> params) {
        return processWebhook(body, params, null, null);
    }

    public boolean processWebhook(Map<String, Object> body, Map<String, String> params, String signature, String requestId) {
        String paymentId = null;

        if (body != null && body.containsKey("data")) {
            Object dataObj = body.get("data");
            if (dataObj instanceof Map<?, ?> dataMap && dataMap.containsKey("id")) {
                paymentId = String.valueOf(dataMap.get("id"));
            }
        }

        if (paymentId == null && params != null) {
            if ("payment".equals(params.get("topic")) || "payment".equals(params.get("type"))) {
                paymentId = params.get("id");
                if (paymentId == null) {
                    paymentId = params.get("data.id");
                }
            }
        }

        if (!isValidSignature(paymentId, requestId, signature)) {
            return false;
        }

        String externalReference = null;

        if (!properties.isMockEnabled()) {
            if (paymentId == null || paymentId.isBlank()) {
                log.warn("Notificacion de Mercado Pago en produccion ignorada: id de pago ausente");
                return false;
            }

            JsonNode paymentDetails = client.getPaymentDetails(paymentId);
            if (paymentDetails == null) {
                log.warn("No se pudo obtener el detalle del pago {} desde la API de Mercado Pago", paymentId);
                return false;
            }

            String paymentStatus = paymentDetails.path("status").asText();
            if (!"approved".equalsIgnoreCase(paymentStatus)) {
                log.info("Notificacion de Mercado Pago recibida con estado no aprobado: {} para pago {}", paymentStatus, paymentId);
                return false;
            }

            externalReference = paymentDetails.path("external_reference").asText(null);
        } else {
            if (body != null && body.containsKey("external_reference")) {
                externalReference = String.valueOf(body.get("external_reference"));
            }
            if (paymentId == null) {
                paymentId = "MOCK-PAY-" + UUID.randomUUID();
            }
        }

        if (externalReference == null || !externalReference.contains(":")) {
            log.warn("Notificacion de Mercado Pago ignorada: sin external_reference valido ({})", externalReference);
            return false;
        }

        String[] parts = externalReference.split(":");
        if (parts.length < 2) {
            log.warn("external_reference con formato invalido: {}", externalReference);
            return false;
        }

        try {
            UUID tenantId = UUID.fromString(parts[0]);
            UUID orderId = UUID.fromString(parts[1]);
            BigDecimal tipPercent = BigDecimal.ZERO;
            if (parts.length >= 4) {
                try {
                    tipPercent = new BigDecimal(parts[2]);
                } catch (Exception ignored) {
                    // Fallback para referencias en formatos anteriores
                }
            }

            final BigDecimal finalTipPercent = tipPercent;
            final String finalPaymentId = paymentId;

            log.info("Procesando pago Mercado Pago aprobado para tenant={} order={} tipPercent={} paymentId={}",
                    tenantId, orderId, finalTipPercent, finalPaymentId);

            TenantContext.callAs(tenantId, () -> transactionTemplate.execute(status -> {
                checkoutService.checkoutMercadoPago(orderId, finalTipPercent, finalPaymentId);
                return null;
            }));

            log.info("Cobro Mercado Pago registrado y orden completada con exito: orderId={}", orderId);
            return true;
        } catch (Exception e) {
            log.error("Fallo al procesar pago Mercado Pago para ref {}: {}", externalReference, e.getMessage(), e);
            return false;
        }
    }

    public PaymentStatusResponse simulateApproval(UUID tenantId, UUID orderId, BigDecimal tipPercent) {
        return TenantContext.callAs(tenantId, () -> transactionTemplate.execute(status -> {
            Ticket ticket = checkoutService.checkoutMercadoPago(
                    orderId,
                    tipPercent != null ? tipPercent : BigDecimal.ZERO,
                    "MOCK-PAYMENT-" + UUID.randomUUID()
            );

            Order order = orderRepository.findById(orderId).orElseThrow();
            return new PaymentStatusResponse(
                    order.getId(),
                    order.getFolio(),
                    order.getStatus().name(),
                    true,
                    ticket.getFolio(),
                    order.getClosedAt()
            );
        }));
    }
}
