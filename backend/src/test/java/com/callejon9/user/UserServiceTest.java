package com.callejon9.user;

import com.callejon9.auth.password.PasswordPolicyViolationException;
import com.callejon9.auth.service.RefreshTokenService;
import com.callejon9.platform.tenant.domain.Tenant;
import com.callejon9.platform.tenant.repository.TenantRepository;
import com.callejon9.platform.tenant.service.PlanLimitService;
import com.callejon9.shared.error.BusinessRuleException;
import com.callejon9.shared.error.InvalidRoleException;
import com.callejon9.shared.error.ResourceNotFoundException;
import com.callejon9.tenancy.TenantContext;
import com.callejon9.user.domain.User;
import com.callejon9.user.domain.UserRole;
import com.callejon9.user.repository.UserRepository;
import com.callejon9.user.service.UserService;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Reglas unitarias de cambios de usuario")
class UserServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private PlanLimitService planLimitService;
    @Mock private TenantRepository tenantRepository;
    @Mock private RefreshTokenService refreshTokenService;
    @InjectMocks private UserService userService;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setTenant() {
        TenantContext.set(tenantId);
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("cambiar el rol guarda el nuevo valor y revoca las sesiones anteriores")
    void roleChangePersistsAndRevokesSessions() {
        User target = user(UserRole.WAITER, true);
        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(target));
        when(userRepository.save(target)).thenReturn(target);

        User updated = userService.setRole(userId, UserRole.CASHIER);

        assertThat(updated.getRole()).isEqualTo(UserRole.CASHIER);
        InOrder order = inOrder(tenantRepository, userRepository, refreshTokenService);
        order.verify(tenantRepository).findByIdForUpdate(tenantId);
        order.verify(userRepository).findByIdForUpdate(userId);
        order.verify(userRepository).save(target);
        order.verify(refreshTokenService).revokeAllSessionsOf(userId);
    }

    @Test
    @DisplayName("un administrador puede ceder el rol si queda otro administrador activo")
    void activeAdminCanBeDemotedWhenAnotherAdminRemains() {
        User target = user(UserRole.ADMIN, true);
        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(target));
        when(userRepository.countByRoleAndActiveTrue(UserRole.ADMIN)).thenReturn(2L);
        when(userRepository.save(target)).thenReturn(target);

        User updated = userService.setRole(userId, UserRole.WAITER);

        assertThat(updated.getRole()).isEqualTo(UserRole.WAITER);
        verify(refreshTokenService).revokeAllSessionsOf(userId);
    }

    @Test
    @DisplayName("no permite degradar al ultimo administrador activo")
    void cannotDemoteLastActiveAdmin() {
        User target = user(UserRole.ADMIN, true);
        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(target));
        when(userRepository.countByRoleAndActiveTrue(UserRole.ADMIN)).thenReturn(1L);

        assertThatThrownBy(() -> userService.setRole(userId, UserRole.CASHIER))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("ultimo administrador activo");

        verify(userRepository, never()).save(target);
        verify(refreshTokenService, never()).revokeAllSessionsOf(userId);
    }

    @Test
    @DisplayName("rechaza SUPER_ADMIN y no modifica la cuenta")
    void cannotAssignPlatformRoleToRestaurantUser() {
        assertThatThrownBy(() -> userService.setRole(userId, UserRole.SUPER_ADMIN))
                .isInstanceOf(InvalidRoleException.class);

        verify(userRepository, never()).findByIdForUpdate(userId);
        verify(userRepository, never()).save(org.mockito.ArgumentMatchers.any(User.class));
    }

    @Test
    void missingRoleIsRejected() {
        assertThatThrownBy(() -> userService.setRole(userId, null))
                .isInstanceOf(InvalidRoleException.class)
                .hasMessage("Debes indicar un rol valido.");

        verify(userRepository, never()).findByIdForUpdate(userId);
    }

    @Test
    @DisplayName("repetir el mismo rol es una operacion sin cambios y conserva la sesion")
    void unchangedRoleDoesNotRevokeSessions() {
        User target = user(UserRole.WAITER, true);
        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(target));

        User unchanged = userService.setRole(userId, UserRole.WAITER);

        assertThat(unchanged.getRole()).isEqualTo(UserRole.WAITER);
        verify(userRepository, never()).save(target);
        verify(refreshTokenService, never()).revokeAllSessionsOf(userId);
    }

    @Test
    @DisplayName("un cambio de contrasena valida y almacena solo el hash")
    void passwordChangeValidatesAndStoresHash() {
        User target = user(UserRole.WAITER, true);
        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(target));
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(restaurant()));
        when(passwordEncoder.encode("Mantel-Azul-47")).thenReturn("encoded-password");
        when(userRepository.save(target)).thenReturn(target);

        userService.changePassword(userId, "Mantel-Azul-47");

        assertThat(target.getPasswordHash()).isEqualTo("encoded-password");
        assertThat(target.getPasswordHash()).doesNotContain("Mantel-Azul-47");
        verify(refreshTokenService).revokeAllSessionsOf(userId);
    }

    @Test
    @DisplayName("rechaza una contrasena debil sin guardar ni revocar sesiones")
    void weakPasswordIsRejected() {
        User target = user(UserRole.WAITER, true);
        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(target));
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(restaurant()));

        assertThatThrownBy(() -> userService.changePassword(userId, "Password2024!"))
                .isInstanceOf(PasswordPolicyViolationException.class)
                .hasMessage("Esa contrasena es demasiado comun. Elige otra.")
                .hasMessageNotContaining("Password2024!");

        verify(passwordEncoder, never()).encode(org.mockito.ArgumentMatchers.anyString());
        verify(userRepository, never()).save(target);
        verify(refreshTokenService, never()).revokeAllSessionsOf(userId);
    }

    @Test
    @DisplayName("rechaza una contrasena que contiene el nombre del usuario")
    void personalNameInPasswordIsRejected() {
        User target = user(UserRole.WAITER, true);
        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(target));
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(restaurant()));

        assertThatThrownBy(() -> userService.changePassword(userId, "Perez-2031x"))
                .isInstanceOf(PasswordPolicyViolationException.class)
                .hasMessage("La contrasena no debe incluir tu nombre, tu correo ni el identificador del restaurante.");

        verify(userRepository, never()).save(target);
        verify(refreshTokenService, never()).revokeAllSessionsOf(userId);
    }

    @Test
    @DisplayName("tambien rechaza una contrasena que contiene el identificador del restaurante")
    void restaurantSlugInPasswordIsRejected() {
        User target = user(UserRole.WAITER, true);
        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(target));
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(restaurant()));

        assertThatThrownBy(() -> userService.changePassword(userId, "Centro-2031x"))
                .isInstanceOf(PasswordPolicyViolationException.class)
                .hasMessageContaining("identificador del restaurante");

        verify(passwordEncoder, never()).encode(org.mockito.ArgumentMatchers.anyString());
        verify(userRepository, never()).save(target);
    }

    @Test
    void missingUserCannotBeEdited() {
        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.setRole(userId, UserRole.CASHIER))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private User user(UserRole role, boolean active) {
        User user = User.builder()
                .email("jperez@correo.mx")
                .passwordHash("old-hash")
                .fullName("Juan Perez")
                .role(role)
                .active(active)
                .totpEnabled(false)
                .build();
        user.setId(userId);
        user.setTenantId(tenantId);
        return user;
    }

    private Tenant restaurant() {
        return Tenant.builder()
                .name("La Esquina")
                .slug("tacos-del-centro")
                .active(true)
                .build();
    }
}
