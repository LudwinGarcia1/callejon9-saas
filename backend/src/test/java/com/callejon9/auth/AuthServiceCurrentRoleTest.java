package com.callejon9.auth;

import com.callejon9.auth.service.AuthService;
import com.callejon9.auth.service.RefreshTokenService;
import com.callejon9.platform.tenant.domain.Tenant;
import com.callejon9.platform.tenant.repository.TenantRepository;
import com.callejon9.user.domain.User;
import com.callejon9.user.domain.UserRole;
import com.callejon9.user.repository.UserRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("El login emite una sesion con el rol vigente")
class AuthServiceCurrentRoleTest {

    @Mock private TenantRepository tenantRepository;
    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private RefreshTokenService refreshTokenService;
    @Mock private TransactionTemplate transactionTemplate;
    @InjectMocks private AuthService authService;

    @Test
    void nextLoginUsesRoleReadFromTheCurrentUserRecord() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Tenant tenant = Tenant.builder().name("La Esquina").slug("la-esquina").active(true).build();
        tenant.setId(tenantId);
        User currentUser = User.builder()
                .email("ana@correo.mx")
                .passwordHash("encoded")
                .fullName("Ana Torres")
                .role(UserRole.CASHIER)
                .active(true)
                .build();
        currentUser.setId(userId);
        currentUser.setTenantId(tenantId);

        when(tenantRepository.findBySlug("la-esquina")).thenReturn(Optional.of(tenant));
        when(userRepository.findByEmailForShare("ana@correo.mx")).thenReturn(Optional.of(currentUser));
        when(passwordEncoder.matches("correcta", "encoded")).thenReturn(true);
        when(refreshTokenService.issueForLogin(currentUser)).thenReturn(
                new RefreshTokenService.IssuedTokens(currentUser, "access", "refresh",
                        Instant.parse("2030-01-01T00:00:00Z")));
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        });

        AuthService.AuthenticatedUser authenticated = authService.authenticate(
                "la-esquina", "ana@correo.mx", "correcta");

        assertThat(authenticated.user().getRole()).isEqualTo(UserRole.CASHIER);
        verify(userRepository).findByEmailForShare("ana@correo.mx");
        verify(refreshTokenService).issueForLogin(currentUser);
    }
}
