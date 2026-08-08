package com.callejon9.inventory.web;

import com.callejon9.inventory.service.InventoryItemService;
import com.callejon9.inventory.web.dto.CreateInventoryItemRequest;
import com.callejon9.inventory.web.dto.InventoryItemResponse;
import com.callejon9.inventory.web.dto.UpdateInventoryItemRequest;
import com.callejon9.inventory.web.dto.UpdateInventoryItemStatusRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/inventory/items")
public class InventoryItemController {

    private final InventoryItemService itemService;

    public InventoryItemController(InventoryItemService itemService) {
        this.itemService = itemService;
    }

    @GetMapping
    public List<InventoryItemResponse> list(
            @RequestParam(required = false, defaultValue = "false") boolean includeInactive) {
        return itemService.listItems(includeInactive).stream()
                .map(InventoryItemResponse::from)
                .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public InventoryItemResponse create(@Valid @RequestBody CreateInventoryItemRequest request) {
        return InventoryItemResponse.from(itemService.createItem(
                request.name(), request.unit(), request.minStock(), request.unitCost()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public InventoryItemResponse update(@PathVariable UUID id,
                                        @Valid @RequestBody UpdateInventoryItemRequest request) {
        return InventoryItemResponse.from(itemService.updateItem(
                id, request.name(), request.unit(), request.minStock(), request.unitCost()));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public InventoryItemResponse patch(@PathVariable UUID id,
                                       @Valid @RequestBody UpdateInventoryItemStatusRequest request) {
        return InventoryItemResponse.from(itemService.setActive(id, request.active()));
    }
}
