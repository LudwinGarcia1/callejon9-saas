package com.callejon9.payment.web;

import com.callejon9.order.domain.Order;
import com.callejon9.order.repository.OrderRepository;
import com.callejon9.payment.service.MercadoPagoService;
import com.callejon9.payment.web.dto.CreatePreferenceRequest;
import com.callejon9.payment.web.dto.PaymentStatusResponse;
import com.callejon9.payment.web.dto.PreferenceResponse;
import com.callejon9.payment.web.dto.SyncPaymentRequest;
import com.callejon9.platform.tenant.domain.Tenant;
import com.callejon9.platform.tenant.repository.TenantRepository;
import com.callejon9.shared.error.ResourceNotFoundException;
import com.callejon9.table.domain.RestaurantTable;
import com.callejon9.table.repository.RestaurantTableRepository;
import com.callejon9.tenancy.TenantContext;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/orders")
public class MercadoPagoController {

    private final MercadoPagoService mercadoPagoService;
    private final OrderRepository orderRepository;
    private final TenantRepository tenantRepository;
    private final RestaurantTableRepository tableRepository;
    private final TransactionTemplate transactionTemplate;

    public MercadoPagoController(
            MercadoPagoService mercadoPagoService,
            OrderRepository orderRepository,
            TenantRepository tenantRepository,
            RestaurantTableRepository tableRepository,
            TransactionTemplate transactionTemplate) {
        this.mercadoPagoService = mercadoPagoService;
        this.orderRepository = orderRepository;
        this.tenantRepository = tenantRepository;
        this.tableRepository = tableRepository;
        this.transactionTemplate = transactionTemplate;
    }

    @PostMapping("/{orderId}/mercadopago/preference")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('CASHIER','ADMIN')")
    public PreferenceResponse createPreference(
            @PathVariable UUID orderId,
            @Valid @RequestBody(required = false) CreatePreferenceRequest request) {

        UUID tenantId = TenantContext.require();

        return transactionTemplate.execute(status -> {
            Tenant tenant = tenantRepository.findById(tenantId)
                    .orElseThrow(() -> new ResourceNotFoundException("El restaurante no existe."));

            Order order = orderRepository.findById(orderId)
                    .orElseThrow(() -> new ResourceNotFoundException("La orden " + orderId + " no existe."));

            Integer tableNumber = null;
            if (order.getTableId() != null) {
                tableNumber = tableRepository.findById(order.getTableId())
                        .map(RestaurantTable::getNumber)
                        .orElse(null);
            }

            var tipPercent = request != null ? request.tipPercent() : null;

            return mercadoPagoService.createPreferenceForOrder(
                    tenantId,
                    orderId,
                    tipPercent,
                    tenant.getName(),
                    tenant.getSlug(),
                    tableNumber
            );
        });
    }

    @GetMapping("/{orderId}/payment-status")
    @PreAuthorize("hasAnyRole('CASHIER','ADMIN','WAITER')")
    public PaymentStatusResponse getPaymentStatus(@PathVariable UUID orderId) {
        UUID tenantId = TenantContext.require();
        return mercadoPagoService.getPaymentStatus(tenantId, orderId);
    }

    @PostMapping("/{orderId}/mercadopago/simulate-approval")
    @PreAuthorize("hasAnyRole('CASHIER','ADMIN')")
    public PaymentStatusResponse simulateApproval(
            @PathVariable UUID orderId,
            @Valid @RequestBody(required = false) CreatePreferenceRequest request) {

        UUID tenantId = TenantContext.require();
        var tipPercent = request != null ? request.tipPercent() : null;
        return mercadoPagoService.simulateApproval(tenantId, orderId, tipPercent);
    }

    @PostMapping("/{orderId}/mercadopago/sync")
    @PreAuthorize("hasAnyRole('CASHIER','ADMIN','WAITER')")
    public PaymentStatusResponse syncPayment(
            @PathVariable UUID orderId,
            @RequestBody(required = false) SyncPaymentRequest request) {

        UUID tenantId = TenantContext.require();
        String paymentId = request != null ? request.paymentId() : null;
        String externalReference = request != null ? request.externalReference() : null;
        return mercadoPagoService.syncPayment(tenantId, orderId, paymentId, externalReference);
    }
}
