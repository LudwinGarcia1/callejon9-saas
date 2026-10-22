package com.callejon9.user.service;

import com.callejon9.auth.password.PasswordPolicy;
import com.callejon9.auth.password.PasswordPolicyViolationException;
import com.callejon9.auth.service.RefreshTokenService;
import com.callejon9.platform.tenant.repository.TenantRepository;
import com.callejon9.platform.tenant.service.PlanLimitService;
import com.callejon9.shared.error.BusinessRuleException;
import com.callejon9.shared.error.InvalidRoleException;
import com.callejon9.shared.error.ResourceNotFoundException;
import com.callejon9.tenancy.TenantContext;
import com.callejon9.user.domain.User;
import com.callejon9.user.domain.UserRole;
import com.callejon9.user.repository.UserRepository;
import java.util.ArrayList;
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
    private final TenantRepository tenantRepository;
    private final RefreshTokenService refreshTokenService;

    public UserService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            PlanLimitService planLimitService,
            TenantRepository tenantRepository,
            RefreshTokenService refreshTokenService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.planLimitService = planLimitService;
        this.tenantRepository = tenantRepository;
        this.refreshTokenService = refreshTokenService;
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
     * Cambia el rol de una cuenta del restaurante y cierra sus sesiones para
     * que los tokens existentes no conserven los permisos anteriores. El
     * siguiente login lee el rol persistido y emite una sesion nueva con el.
     */
    @Transactional
    public User setRole(UUID userId, UserRole role) {
        if (role == null) {
            throw new InvalidRoleException("Debes indicar un rol valido.");
        }
        if (role == UserRole.SUPER_ADMIN) {
            throw new InvalidRoleException(
                    "El rol SUPER_ADMIN pertenece a la plataforma y no puede asignarse desde un restaurante.");
        }

        tenantRepository.findByIdForUpdate(TenantContext.require());
        User target = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new ResourceNotFoundException("El usuario no existe."));

        if (target.getRole() == role) {
            return target;
        }

        if (target.isActive()
                && target.getRole() == UserRole.ADMIN
                && userRepository.countByRoleAndActiveTrue(UserRole.ADMIN) <= 1) {
            throw new BusinessRuleException(
                    "No se puede cambiar el rol del ultimo administrador activo del restaurante.");
        }

        target.setRole(role);
        User saved = userRepository.save(target);
        refreshTokenService.revokeAllSessionsOf(saved.getId());
        return saved;
    }

    /**
     * Actualiza la contrasena solo despues de validarla contra los datos ya
     * guardados de la cuenta y su restaurante. Las sesiones anteriores se
     * revocan junto con el cambio, en la misma transaccion.
     */
    @Transactional
    public void changePassword(UUID userId, String rawPassword) {
        User target = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new ResourceNotFoundException("El usuario no existe."));

        if (rawPassword == null || rawPassword.isBlank()) {
            throw new PasswordPolicyViolationException("Ingresa una contrasena.");
        }

        List<String> personalData = new ArrayList<>();
        personalData.add(target.getEmail());
        personalData.add(target.getFullName());
        tenantRepository.findById(TenantContext.require()).ifPresent(tenant -> {
            personalData.add(tenant.getName());
            personalData.add(tenant.getSlug());
        });

        PasswordPolicy.check(rawPassword, personalData).ifPresent(message -> {
            throw new PasswordPolicyViolationException(message);
        });

        target.setPasswordHash(passwordEncoder.encode(rawPassword));
        userRepository.save(target);
        refreshTokenService.revokeAllSessionsOf(target.getId());
    }

    /**
     * Activa o desactiva a un usuario. La desactivacion es siempre logica
     * (active = false): el usuario queda referenciado por las ordenes que
     * tomo y las ventas que cobro, y borrarlo perderia esa atribucion.
     *
     * <p>Bloquea la fila del tenant ANTES de contar administradores activos:
     * sin este lock, dos peticiones concurrentes que desactivan a dos
     * administradores distintos pueden leer ambas el mismo conteo (2), pasar
     * las dos la validacion y dejar el restaurante sin ningun administrador
     * activo. Con el lock, la segunda espera a que la primera confirme y
     * relee el conteo ya actualizado.
     *
     * <p>Desactivar revoca ademas todas las sesiones del usuario: sin esto,
     * su access token seguiria autorizando hasta 15 minutos despues de la
     * baja. La revocacion va despues de las reglas de negocio y en la misma
     * transaccion, asi que una desactivacion rechazada no cierra ninguna
     * sesion. Reactivar no las restaura: el usuario vuelve a iniciar sesion.
     */
    @Transactional
    public User setActive(UUID userId, boolean active, UUID callerId) {
        tenantRepository.findByIdForUpdate(TenantContext.require());

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
        User saved = userRepository.save(target);

        if (!active) {
            // El UPDATE masivo vacia el contexto de persistencia tras volcar el
            // cambio de estado; `saved` queda desconectado pero con ese valor.
            refreshTokenService.revokeAllSessionsOf(saved.getId());
        }
        return saved;
    }
}
