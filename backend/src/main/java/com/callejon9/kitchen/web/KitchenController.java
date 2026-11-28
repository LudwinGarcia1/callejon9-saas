package com.callejon9.kitchen.web;

import com.callejon9.kitchen.service.KitchenService;
import com.callejon9.kitchen.web.dto.KitchenItemResponse;
import com.callejon9.kitchen.web.dto.KitchenOrderResponse;
import com.callejon9.kitchen.web.dto.UpdateKitchenItemStatusRequest;
import com.callejon9.order.domain.KitchenItemStatus;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/kitchen")
public class KitchenController {

    private final KitchenService kitchenService;

    public KitchenController(KitchenService kitchenService) {
        this.kitchenService = kitchenService;
    }

    @GetMapping("/orders")
    @PreAuthorize("hasAnyRole('KITCHEN','ADMIN')")
    public List<KitchenOrderResponse> listSentOrders() {
        return kitchenService.listSentOrders().stream().map(KitchenOrderResponse::from).toList();
    }

    /**
     * Cocina y administracion avanzan cualquier paso. El mesero solo entrega:
     * puede llevar un producto LISTO a ENTREGADO, pero no marcar como en
     * preparacion o listo lo que cocina no ha preparado. Como la transicion
     * es solo hacia adelante, pedir DELIVERED implica partir de READY.
     */
    @PostMapping("/items/{itemId}/status")
    @PreAuthorize("hasAnyRole('KITCHEN','ADMIN','WAITER')")
    public KitchenItemResponse advanceItemStatus(
            @PathVariable UUID itemId, @Valid @RequestBody UpdateKitchenItemStatusRequest request,
            Authentication authentication) {
        if (isWaiterOnly(authentication) && request.status() != KitchenItemStatus.DELIVERED) {
            throw new AccessDeniedException("El mesero solo puede marcar un producto como entregado.");
        }
        return KitchenItemResponse.from(kitchenService.advanceItemStatus(itemId, request.status()));
    }

    private static boolean isWaiterOnly(Authentication authentication) {
        Set<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());
        return roles.contains("ROLE_WAITER") && !roles.contains("ROLE_KITCHEN") && !roles.contains("ROLE_ADMIN");
    }
}
