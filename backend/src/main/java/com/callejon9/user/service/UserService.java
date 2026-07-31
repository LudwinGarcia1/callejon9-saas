package com.callejon9.user.service;

import com.callejon9.platform.tenant.service.PlanLimitService;
import com.callejon9.shared.error.BusinessRuleException;
import com.callejon9.shared.error.InvalidRoleException;
import com.callejon9.shared.error.ResourceNotFoundException;
import com.callejon9.tenancy.TenantContext;
import com.callejon9.user.domain.User;
import com.callejon9.user.domain.UserRole;
import com.callejon9.user.repository.UserRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Administra los usuarios del restaurante activo (el tenant ya lo fijo
 * {@code TenantFilter} antes de llegar aqui, asi que un simple
 * {@code @Transactional} basta: no hay dos transacciones como en
 * {@code TenantOnboardingService}, que corre antes de que exista tenant.
 */
@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final PlanLimitService planLimitService;

    public UserService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            PlanLimitService planLimitService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.planLimitService = planLimitService;
    }

    @Transactional
    public User createUser(String email, String fullName, UserRole role, String rawPassword) {
        if (role == UserRole.SUPER_ADMIN) {
            throw new InvalidRoleException(
                    "El rol SUPER_ADMIN pertenece al tenant tecnico de la plataforma "
                            + "y no puede asignarse desde un restaurante.");
        }

        if (userRepository.findByEmail(email).isPresent()) {
            throw new BusinessRuleException(
                    "Ya existe un usuario con el correo '" + email + "' en este restaurante.");
        }

        planLimitService.assertCanAddUser(TenantContext.require());

        return userRepository.save(User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(rawPassword))
                .fullName(fullName)
                .role(role)
                .active(true)
                .totpEnabled(false)
                .build());
    }

    @Transactional(readOnly = true)
    public List<User> listUsers() {
        return userRepository.findAll(Sort.by("fullName"));
    }

    /**
     * Activa o desactiva a un usuario. La desactivacion es siempre logica
     * (active = false): el usuario queda referenciado por las ordenes que
     * tomo y las ventas que cobro, y borrarlo perderia esa atribucion.
     */
    @Transactional
    public User setActive(UUID userId, boolean active, UUID callerId) {
        User target = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("El usuario no existe."));

        if (!active) {
            if (target.getId().equals(callerId)) {
                throw new BusinessRuleException(
                        "Un administrador no puede desactivarse a si mismo.");
            }

            if (target.getRole() == UserRole.ADMIN
                    && target.isActive()
                    && userRepository.countByRoleAndActiveTrue(UserRole.ADMIN) <= 1) {
                throw new BusinessRuleException(
                        "No se puede desactivar al ultimo administrador activo del restaurante.");
            }
        }

        target.setActive(active);
        return userRepository.save(target);
    }
}
