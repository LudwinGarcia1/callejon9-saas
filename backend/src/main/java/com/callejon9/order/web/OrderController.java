package com.callejon9.order.web;

import com.callejon9.order.domain.OrderStatus;
import com.callejon9.order.service.NewOrderItem;
import com.callejon9.order.service.OrderService;
import com.callejon9.order.web.dto.AddOrderItemsRequest;
import com.callejon9.order.web.dto.OpenOrderRequest;
import com.callejon9.order.web.dto.OrderResponse;
import com.callejon9.order.web.dto.OrderSummaryResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** El principal autenticado es el UUID del usuario (ver TenantFilter); nunca se confia en el body. */
@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('WAITER','ADMIN')")
    public OrderResponse open(@Valid @RequestBody OpenOrderRequest request,
                               Authentication authentication) {
        var order = orderService.openOrder(
                request.tableId(), request.guestCount(), waiterIdOf(authentication));
        return OrderResponse.from(orderService.getOrder(order.getId()));
    }

    @GetMapping("/{id}")
    public OrderResponse get(@PathVariable UUID id) {
        return OrderResponse.from(orderService.getOrder(id));
    }

    @GetMapping
    public List<OrderSummaryResponse> list(@RequestParam(required = false) OrderStatus status) {
        return orderService.listOrders(status).stream()
                .map(OrderSummaryResponse::from)
                .toList();
    }

    @PostMapping("/{id}/items")
    @PreAuthorize("hasAnyRole('WAITER','ADMIN')")
    public OrderResponse addItems(@PathVariable UUID id,
                                   @Valid @RequestBody AddOrderItemsRequest request) {
        List<NewOrderItem> items = request.items().stream()
                .map(item -> new NewOrderItem(item.productId(), item.quantity(), item.notes()))
                .toList();
        return OrderResponse.from(orderService.addItems(id, items));
    }

    @PostMapping("/{id}/send-to-kitchen")
    @PreAuthorize("hasAnyRole('WAITER','ADMIN')")
    public OrderResponse sendToKitchen(@PathVariable UUID id) {
        return OrderResponse.from(orderService.sendToKitchen(id));
    }

    private UUID waiterIdOf(Authentication authentication) {
        return (UUID) authentication.getPrincipal();
    }
}
