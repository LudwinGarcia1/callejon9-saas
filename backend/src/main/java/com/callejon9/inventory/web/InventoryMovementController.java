package com.callejon9.inventory.web;

import com.callejon9.inventory.domain.InventoryMovement;
import com.callejon9.inventory.service.InventoryMovementService;
import com.callejon9.inventory.web.dto.InventoryMovementRow;
import com.callejon9.inventory.web.dto.RegisterMovementRequest;
import com.callejon9.inventory.web.dto.RegisteredMovementResponse;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** El principal autenticado es el UUID del usuario (ver TenantFilter); nunca se confia en el body. */
@RestController
@RequestMapping("/api/v1/inventory/movements")
public class InventoryMovementController {

    private final InventoryMovementService movementService;

    public InventoryMovementController(InventoryMovementService movementService) {
        this.movementService = movementService;
    }

    @GetMapping
    public List<InventoryMovementRow> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) UUID itemId) {
        return movementService.listMovements(from, to, itemId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','KITCHEN')")
    public RegisteredMovementResponse register(@Valid @RequestBody RegisterMovementRequest request,
                                               Authentication authentication) {
        InventoryMovement movement = movementService.register(
                request.inventoryItemId(), request.movementType(),
                request.quantity(), request.countedStock(), request.reason(),
                userIdOf(authentication));
        return RegisteredMovementResponse.from(movement);
    }

    private UUID userIdOf(Authentication authentication) {
        return (UUID) authentication.getPrincipal();
    }
}
