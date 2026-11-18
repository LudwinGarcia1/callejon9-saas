package com.callejon9.order.web;

import com.callejon9.order.domain.OrderStatus;
import com.callejon9.order.service.NewOrderItem;
import com.callejon9.order.service.OrderActor;
import com.callejon9.order.service.OrderService;
import com.callejon9.order.web.dto.AddOrderItemsRequest;
import com.callejon9.order.web.dto.OpenOrderRequest;
import com.callejon9.order.web.dto.OrderResponse;
import com.callejon9.order.web.dto.OrderSummaryResponse;
import com.callejon9.shared.web.PageQuery;
import com.callejon9.shared.web.PageResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
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

    private static final Map<String, String> SORTABLE =
            Map.of("openedAt", "openedAt", "folio", "folio", "total", "total");

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
    @PreAuthorize("hasAnyRole('WAITER','CASHIER','ADMIN')")
    public OrderResponse get(@PathVariable UUID id) {
        return OrderResponse.from(orderService.getOrder(id));
    }

    /**
     * Paginado (CAL-16): {@code ?status} o {@code ?open=true}, mas
     * {@code page}, {@code size} (1 a {@value PageQuery#MAX_SIZE}) y
     * {@code sort} sobre {@code openedAt}, {@code folio} o {@code total}. Por
     * defecto, la mas reciente primero.
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('WAITER','CASHIER','ADMIN')")
    public PageResponse<OrderSummaryResponse> list(
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(defaultValue = "false") boolean open,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        Pageable pageable = PageQuery.of(page, size, sort, SORTABLE, Sort.by(Sort.Direction.DESC, "openedAt"));
        return PageResponse.from(orderService.listOrders(status, open, pageable), OrderSummaryResponse::from);
    }

    @PostMapping("/{id}/items")
    @PreAuthorize("hasAnyRole('WAITER','ADMIN')")
    public OrderResponse addItems(@PathVariable UUID id,
                                   @Valid @RequestBody AddOrderItemsRequest request,
                                   Authentication authentication) {
        List<NewOrderItem> items = request.items().stream()
                .map(item -> new NewOrderItem(item.productId(), item.quantity(), item.notes()))
                .toList();
        return OrderResponse.from(orderService.addItems(id, items, actorOf(authentication)));
    }

    @DeleteMapping("/{id}/items/{itemId}")
    @PreAuthorize("hasAnyRole('WAITER','ADMIN')")
    public OrderResponse removeItem(@PathVariable UUID id, @PathVariable UUID itemId,
                                     Authentication authentication) {
        return OrderResponse.from(orderService.removeItem(id, itemId, actorOf(authentication)));
    }

    @PostMapping("/{id}/send-to-kitchen")
    @PreAuthorize("hasAnyRole('WAITER','ADMIN')")
    public OrderResponse sendToKitchen(@PathVariable UUID id, Authentication authentication) {
        return OrderResponse.from(orderService.sendToKitchen(id, actorOf(authentication)));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('WAITER','ADMIN')")
    public OrderResponse cancel(@PathVariable UUID id, Authentication authentication) {
        return OrderResponse.from(orderService.cancelOrder(id, actorOf(authentication)));
    }

    private UUID waiterIdOf(Authentication authentication) {
        return (UUID) authentication.getPrincipal();
    }

    private OrderActor actorOf(Authentication authentication) {
        boolean admin = authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
        return new OrderActor(waiterIdOf(authentication), admin);
    }
}
