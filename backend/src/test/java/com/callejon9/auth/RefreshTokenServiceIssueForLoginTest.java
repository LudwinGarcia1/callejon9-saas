package com.callejon9.auth;

import com.callejon9.auth.repository.RefreshTokenRepository;
import com.callejon9.auth.service.JwtService;
import com.callejon9.auth.service.RefreshTokenService;
import com.callejon9.platform.tenant.repository.TenantRepository;
import com.callejon9.tenancy.TenantContext;
import com.callejon9.user.domain.User;
import com.callejon9.user.domain.UserRole;
import com.callejon9.user.repository.UserRepository;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Emision de sesion con datos actuales del usuario")
class RefreshTokenServiceIssueForLoginTest {

    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private UserRepository userRepository;
    @Mock private TenantRepository tenantRepository;
    @Mock private JwtService jwtService;
    @Mock private TransactionTemplate transactionTemplate;
    @InjectMocks private RefreshTokenService refreshTokenService;

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void tokenClaimsUseRoleFromLockedUserRatherThanEarlierLoginRead() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        TenantContext.set(tenantId);
        User earlierRead = user(userId, tenantId, UserRole.WAITER);
        User lockedCurrent = user(userId, tenantId, UserRole.CASHIER);

        when(userRepository.findByIdForShare(userId)).thenReturn(Optional.of(lockedCurrent));
        when(jwtService.refreshTokenTtl()).thenReturn(Duration.ofDays(30));
        when(jwtService.generateRefreshToken(any(), any(), anyString(), any()))
                .thenReturn("signed-refresh");
        when(jwtService.generateAccessToken(any(User.class), any())).thenReturn("signed-access");
        when(refreshTokenRepository.save(any())).thenAnswer(invocation -> {
            var token = invocation.getArgument(0,
                    com.callejon9.auth.domain.RefreshToken.class);
            token.setId(UUID.randomUUID());
            return token;
        });
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        });

        RefreshTokenService.IssuedTokens issued = refreshTokenService.issueForLogin(earlierRead);

        assertThat(issued.user()).isSameAs(lockedCurrent);
        assertThat(issued.user().getRole()).isEqualTo(UserRole.CASHIER);
        verify(jwtService).generateAccessToken(same(lockedCurrent), any());
    }

    private User user(UUID userId, UUID tenantId, UserRole role) {
        User user = User.builder()
                .email("ana@correo.mx")
                .passwordHash("encoded")
                .fullName("Ana Torres")
                .role(role)
                .active(true)
                .build();
        user.setId(userId);
        user.setTenantId(tenantId);
        return user;
    }
}
