package com.callejon9.auth.service;

import com.callejon9.auth.domain.RefreshToken;
import com.callejon9.auth.repository.RefreshTokenRepository;
import com.callejon9.platform.tenant.domain.Tenant;
import com.callejon9.platform.tenant.repository.TenantRepository;
import com.callejon9.user.domain.User;
import com.callejon9.user.repository.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * La sesión dura 7 días desde el login y renovar no la alarga. Con un reloj
 * fijo se prueba el borde exacto: un instante antes del vencimiento se renueva;
 * en el instante del vencimiento, ya no.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("UT · vida máxima de la sesión (refresh token)")
class RefreshTokenSessionExpiryTest {

    private static final Instant LOGIN = Instant.parse("2026-10-14T18:00:00Z");
    private static final Duration SESSION = Duration.ofDays(7);
    private static final Instant SESSION_END = LOGIN.plus(SESSION);

    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private UserRepository userRepository;
    @Mock private TenantRepository tenantRepository;
    @Mock private JwtService jwtService;
    @Mock private TransactionTemplate transactionTemplate;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID familyId = UUID.randomUUID();
    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder().email("mesero@pruebas.com").fullName("Mesero de piso").active(true).build();
        user.setId(UUID.randomUUID());
        user.setTenantId(tenantId);
        Tenant tenant = Tenant.builder().name("Callejon 9 pruebas").slug("pruebas").active(true).build();
        tenant.setId(tenantId);

        when(transactionTemplate.execute(any())).thenAnswer(invocation ->
                invocation.<TransactionCallback<?>>getArgument(0).doInTransaction(null));
        when(jwtService.refreshTokenTtl()).thenReturn(SESSION);
        when(jwtService.generateAccessToken(any(), any())).thenReturn("access-token");
        when(jwtService.generateRefreshToken(any(), any(), anyString(), any())).thenReturn("refresh-token");
        when(jwtService.parseRefreshToken("refresh-presentado"))
                .thenReturn(new JwtService.RefreshClaims(user.getId(), tenantId, "secreto"));
        when(userRepository.findByIdForShare(user.getId())).thenReturn(Optional.of(user));
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(refreshTokenRepository.save(any())).thenAnswer(invocation -> {
            RefreshToken saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });
        when(refreshTokenRepository.consume(any(), any())).thenReturn(1);
    }

    private RefreshTokenService serviceAt(Instant now) {
        return new RefreshTokenService(refreshTokenRepository, userRepository, tenantRepository,
                jwtService, transactionTemplate, Clock.fixed(now, ZoneOffset.UTC));
    }

    /** Token vigente de la sesión que empezó en LOGIN, guardado con su vencimiento absoluto. */
    private void storedTokenExpiringAt(Instant expiresAt) {
        RefreshToken token = RefreshToken.builder().userId(user.getId()).tokenHash(RefreshTokenService.hash("secreto"))
                .familyId(familyId).expiresAt(expiresAt).build();
        token.setId(UUID.randomUUID());
        token.setTenantId(tenantId);
        when(refreshTokenRepository.findByTokenHash(RefreshTokenService.hash("secreto"))).thenReturn(Optional.of(token));
    }

    @Test
    @DisplayName("el login abre una sesión que vence exactamente 7 días después")
    void loginOpensASevenDaySession() {
        RefreshTokenService.IssuedTokens issued = serviceAt(LOGIN).issueForLogin(user);

        assertThat(issued.sessionExpiresAt()).isEqualTo(SESSION_END);
        verify(jwtService).generateRefreshToken(eq(user.getId()), eq(tenantId), anyString(), eq(SESSION_END));
        ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(saved.capture());
        assertThat(saved.getValue().getExpiresAt()).isEqualTo(SESSION_END);
    }

    @Test
    @DisplayName("un instante antes del vencimiento renueva y el token nuevo hereda el mismo vencimiento")
    void renewsJustBeforeTheEndWithoutExtendingTheSession() {
        storedTokenExpiringAt(SESSION_END);

        RefreshTokenService.IssuedTokens rotated = serviceAt(SESSION_END.minusNanos(1)).rotate("refresh-presentado");

        assertThat(rotated.sessionExpiresAt()).isEqualTo(SESSION_END);
        ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(saved.capture());
        assertThat(saved.getValue().getExpiresAt()).isEqualTo(SESSION_END);
        assertThat(saved.getValue().getFamilyId()).isEqualTo(familyId);
        verify(jwtService).generateRefreshToken(eq(user.getId()), eq(tenantId), anyString(), eq(SESSION_END));
    }

    @Test
    @DisplayName("en el instante exacto del vencimiento rechaza la renovación sin consumir el token")
    void rejectsExactlyAtTheEnd() {
        storedTokenExpiringAt(SESSION_END);

        assertThatThrownBy(() -> serviceAt(SESSION_END).rotate("refresh-presentado"))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Sesion no renovable.");
        verify(refreshTokenRepository, never()).consume(any(), any());
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    @DisplayName("después del vencimiento rechaza la renovación")
    void rejectsAfterTheEnd() {
        storedTokenExpiringAt(SESSION_END);

        assertThatThrownBy(() -> serviceAt(SESSION_END.plusSeconds(1)).rotate("refresh-presentado"))
                .isInstanceOf(BadCredentialsException.class);
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    @DisplayName("renovar al tercer día no mueve el vencimiento: sigue siendo el del login")
    void renewingMidSessionKeepsTheOriginalEnd() {
        storedTokenExpiringAt(SESSION_END);

        RefreshTokenService.IssuedTokens rotated = serviceAt(LOGIN.plus(Duration.ofDays(3))).rotate("refresh-presentado");

        assertThat(rotated.sessionExpiresAt()).isEqualTo(SESSION_END).isNotEqualTo(LOGIN.plus(Duration.ofDays(10)));
    }
}
