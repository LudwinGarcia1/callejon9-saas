package com.callejon9.payment.web;

import com.callejon9.order.domain.Order;
import com.callejon9.order.domain.OrderItem;
import com.callejon9.order.domain.OrderStatus;
import com.callejon9.order.repository.OrderItemRepository;
import com.callejon9.order.repository.OrderRepository;
import com.callejon9.order.service.OrderWithItems;
import com.callejon9.order.web.dto.OrderResponse;
import com.callejon9.payment.service.MercadoPagoService;
import com.callejon9.payment.web.dto.CreatePreferenceRequest;
import com.callejon9.payment.web.dto.PaymentStatusResponse;
import com.callejon9.payment.web.dto.PreferenceResponse;
import com.callejon9.platform.tenant.domain.Tenant;
import com.callejon9.platform.tenant.repository.TenantRepository;
import com.callejon9.shared.error.BusinessRuleException;
import com.callejon9.shared.error.ResourceNotFoundException;
import com.callejon9.table.domain.RestaurantTable;
import com.callejon9.table.repository.RestaurantTableRepository;
import com.callejon9.tenancy.TenantContext;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public")
public class PublicMercadoPagoController {

    private final TenantRepository tenantRepository;
    private final RestaurantTableRepository tableRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final MercadoPagoService mercadoPagoService;
    private final TransactionTemplate transactionTemplate;

    public PublicMercadoPagoController(
            TenantRepository tenantRepository,
            RestaurantTableRepository tableRepository,
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            MercadoPagoService mercadoPagoService,
            TransactionTemplate transactionTemplate) {
        this.tenantRepository = tenantRepository;
        this.tableRepository = tableRepository;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.mercadoPagoService = mercadoPagoService;
        this.transactionTemplate = transactionTemplate;
    }

    @GetMapping("/{slug}/tables/{tableNumber}/order")
    public OrderResponse getActiveTableOrder(
            @PathVariable String slug,
            @PathVariable int tableNumber) {

        Tenant tenant = tenantRepository.findBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException("El restaurante '" + slug + "' no existe."));

        return TenantContext.callAs(tenant.getId(), () -> transactionTemplate.execute(status -> {
            RestaurantTable table = tableRepository.findByNumber(tableNumber)
                    .orElseThrow(() -> new ResourceNotFoundException("La mesa " + tableNumber + " no existe."));

            List<OrderStatus> openStatuses = List.of(OrderStatus.NEW, OrderStatus.SENT, OrderStatus.READY);
            Order order = orderRepository.findFirstByTableIdAndStatusInOrderByOpenedAtDesc(table.getId(), openStatuses)
                    .orElseThrow(() -> new ResourceNotFoundException("La mesa " + tableNumber + " no tiene ninguna orden activa."));

            List<OrderItem> items = orderItemRepository.findByOrderIdOrderByOrderedAt(order.getId());
            return OrderResponse.from(new OrderWithItems(order, items));
        }));
    }

    @PostMapping("/{slug}/tables/{tableNumber}/mercadopago/preference")
    @ResponseStatus(HttpStatus.CREATED)
    public PreferenceResponse createTablePreference(
            @PathVariable String slug,
            @PathVariable int tableNumber,
            @Valid @RequestBody(required = false) CreatePreferenceRequest request) {

        Tenant tenant = tenantRepository.findBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException("El restaurante '" + slug + "' no existe."));

        return TenantContext.callAs(tenant.getId(), () -> {
            RestaurantTable table = tableRepository.findByNumber(tableNumber)
                    .orElseThrow(() -> new ResourceNotFoundException("La mesa " + tableNumber + " no existe."));

            List<OrderStatus> openStatuses = List.of(OrderStatus.NEW, OrderStatus.SENT, OrderStatus.READY);
            Order order = orderRepository.findFirstByTableIdAndStatusInOrderByOpenedAtDesc(table.getId(), openStatuses)
                    .orElseThrow(() -> new BusinessRuleException("La mesa " + tableNumber + " no tiene ninguna comanda pendiente de pago."));

            var tipPercent = request != null ? request.tipPercent() : null;

            return mercadoPagoService.createPreferenceForOrder(
                    tenant.getId(),
                    order.getId(),
                    tipPercent,
                    tenant.getName(),
                    tenant.getSlug(),
                    tableNumber
            );
        });
    }

    @GetMapping("/{slug}/tables/{tableNumber}/payment-status")
    public PaymentStatusResponse getTablePaymentStatus(
            @PathVariable String slug,
            @PathVariable int tableNumber) {

        Tenant tenant = tenantRepository.findBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException("El restaurante '" + slug + "' no existe."));

        return TenantContext.callAs(tenant.getId(), () -> {
            RestaurantTable table = tableRepository.findByNumber(tableNumber)
                    .orElseThrow(() -> new ResourceNotFoundException("La mesa " + tableNumber + " no existe."));

            List<OrderStatus> allStatuses = List.of(OrderStatus.NEW, OrderStatus.SENT, OrderStatus.READY, OrderStatus.PAID);
            Order order = orderRepository.findFirstByTableIdAndStatusInOrderByOpenedAtDesc(table.getId(), allStatuses)
                    .orElseThrow(() -> new ResourceNotFoundException("No se encontró ninguna orden para la mesa " + tableNumber));

            return mercadoPagoService.getPaymentStatus(tenant.getId(), order.getId());
        });
    }

    @PostMapping("/{slug}/tables/{tableNumber}/mercadopago/simulate-approval")
    public PaymentStatusResponse simulateTableApproval(
            @PathVariable String slug,
            @PathVariable int tableNumber,
            @Valid @RequestBody(required = false) CreatePreferenceRequest request) {

        Tenant tenant = tenantRepository.findBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException("El restaurante '" + slug + "' no existe."));

        return TenantContext.callAs(tenant.getId(), () -> {
            RestaurantTable table = tableRepository.findByNumber(tableNumber)
                    .orElseThrow(() -> new ResourceNotFoundException("La mesa " + tableNumber + " no existe."));

            List<OrderStatus> openStatuses = List.of(OrderStatus.NEW, OrderStatus.SENT, OrderStatus.READY);
            Order order = orderRepository.findFirstByTableIdAndStatusInOrderByOpenedAtDesc(table.getId(), openStatuses)
                    .orElseThrow(() -> new BusinessRuleException("La mesa no tiene comanda activa por pagar."));

            var tipPercent = request != null ? request.tipPercent() : null;
            return mercadoPagoService.simulateApproval(tenant.getId(), order.getId(), tipPercent);
        });
    }
}
