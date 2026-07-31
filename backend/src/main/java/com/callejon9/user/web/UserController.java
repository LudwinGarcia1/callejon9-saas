package com.callejon9.user.web;

import com.callejon9.user.service.UserService;
import com.callejon9.user.web.dto.CreateUserRequest;
import com.callejon9.user.web.dto.UpdateUserStatusRequest;
import com.callejon9.user.web.dto.UserResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** El principal autenticado es el UUID del usuario (ver TenantFilter); nunca se confia en el body. */
@RestController
@RequestMapping("/api/v1/users")
@PreAuthorize("hasRole('ADMIN')")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse create(@Valid @RequestBody CreateUserRequest request) {
        var user = userService.createUser(
                request.email(), request.fullName(), request.role(), request.password());
        return UserResponse.from(user);
    }

    @GetMapping
    public List<UserResponse> list() {
        return userService.listUsers().stream()
                .map(UserResponse::from)
                .toList();
    }

    @PatchMapping("/{id}")
    public UserResponse patch(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateUserStatusRequest request,
            Authentication authentication) {
        var user = userService.setActive(id, request.active(), callerIdOf(authentication));
        return UserResponse.from(user);
    }

    private UUID callerIdOf(Authentication authentication) {
        return (UUID) authentication.getPrincipal();
    }
}
