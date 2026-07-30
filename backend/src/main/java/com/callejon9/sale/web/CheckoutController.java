package com.callejon9.sale.web;

import com.callejon9.sale.service.CheckoutService;
import com.callejon9.sale.web.dto.CheckoutRequest;
import com.callejon9.ticket.web.dto.TicketResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** El principal autenticado es el UUID del usuario (ver TenantFilter); nunca se confia en el body. */
@RestController
@RequestMapping("/api/v1/orders")
public class CheckoutController {

    private final CheckoutService checkoutService;

    public CheckoutController(CheckoutService checkoutService) {
        this.checkoutService = checkoutService;
    }

    @PostMapping("/{id}/checkout")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('CASHIER','ADMIN')")
    public TicketResponse checkout(
            @PathVariable UUID id,
            @Valid @RequestBody CheckoutRequest request,
            Authentication authentication) {
        var ticket = checkoutService.checkout(
                id, request.paymentMethod(), request.tipPercent(), cashierIdOf(authentication));
        return TicketResponse.from(ticket);
    }

    private UUID cashierIdOf(Authentication authentication) {
        return (UUID) authentication.getPrincipal();
    }
}
