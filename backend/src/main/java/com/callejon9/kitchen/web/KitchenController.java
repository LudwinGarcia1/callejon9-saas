package com.callejon9.kitchen.web;

import com.callejon9.kitchen.service.KitchenService;
import com.callejon9.kitchen.web.dto.KitchenItemResponse;
import com.callejon9.kitchen.web.dto.KitchenOrderResponse;
import com.callejon9.kitchen.web.dto.UpdateKitchenItemStatusRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/kitchen")
@PreAuthorize("hasAnyRole('KITCHEN','ADMIN')")
public class KitchenController {

    private final KitchenService kitchenService;

    public KitchenController(KitchenService kitchenService) {
        this.kitchenService = kitchenService;
    }

    @GetMapping("/orders")
    public List<KitchenOrderResponse> listSentOrders() {
        return kitchenService.listSentOrders().stream().map(KitchenOrderResponse::from).toList();
    }

    @PostMapping("/items/{itemId}/status")
    public KitchenItemResponse advanceItemStatus(
            @PathVariable UUID itemId, @Valid @RequestBody UpdateKitchenItemStatusRequest request) {
        return KitchenItemResponse.from(kitchenService.advanceItemStatus(itemId, request.status()));
    }
}
