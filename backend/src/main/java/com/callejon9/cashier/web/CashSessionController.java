package com.callejon9.cashier.web;

import com.callejon9.cashier.service.CashSessionService;
import com.callejon9.cashier.web.dto.CashMovementRequest;
import com.callejon9.cashier.web.dto.CashSessionListResponse;
import com.callejon9.cashier.web.dto.CashSessionResponse;
import com.callejon9.cashier.web.dto.CloseCashSessionRequest;
import com.callejon9.cashier.web.dto.OpenCashSessionRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/cash-sessions")
@PreAuthorize("hasAnyRole('CASHIER','ADMIN')")
public class CashSessionController {

    private final CashSessionService cashSessionService;

    public CashSessionController(CashSessionService cashSessionService) {
        this.cashSessionService = cashSessionService;
    }

    @GetMapping
    public CashSessionListResponse list(Authentication authentication) {
        return cashSessionService.list(userId(authentication), isAdministrator(authentication));
    }

    @GetMapping("/{id}")
    public CashSessionResponse get(@PathVariable UUID id, Authentication authentication) {
        return cashSessionService.get(id, userId(authentication), isAdministrator(authentication));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CashSessionResponse open(
            @Valid @RequestBody OpenCashSessionRequest request, Authentication authentication) {
        return cashSessionService.open(userId(authentication), request);
    }

    @PostMapping("/{id}/close")
    public CashSessionResponse closeOwn(
            @PathVariable UUID id,
            @Valid @RequestBody CloseCashSessionRequest request,
            Authentication authentication) {
        return cashSessionService.closeOwn(id, userId(authentication), request);
    }

    @PostMapping("/{id}/force-close")
    @PreAuthorize("hasRole('ADMIN')")
    public CashSessionResponse forceClose(
            @PathVariable UUID id,
            @Valid @RequestBody CloseCashSessionRequest request,
            Authentication authentication) {
        return cashSessionService.forceClose(id, userId(authentication), request);
    }

    @PostMapping("/{id}/movements")
    @ResponseStatus(HttpStatus.CREATED)
    public CashSessionResponse addMovement(
            @PathVariable UUID id,
            @Valid @RequestBody CashMovementRequest request,
            Authentication authentication) {
        return cashSessionService.addMovement(
                id, userId(authentication), isAdministrator(authentication), request);
    }

    private UUID userId(Authentication authentication) {
        return (UUID) authentication.getPrincipal();
    }

    private boolean isAdministrator(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_ADMIN"::equals);
    }
}
