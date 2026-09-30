package com.callejon9.auth.service;

import com.callejon9.auth.domain.RefreshToken;
import com.callejon9.auth.repository.RefreshTokenRepository;
import com.callejon9.platform.tenant.domain.Tenant;
import com.callejon9.platform.tenant.repository.TenantRepository;
import com.callejon9.tenancy.TenantContext;
import com.callejon9.user.domain.User;
import com.callejon9.user.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Emite, rota y revoca refresh tokens.
 *
 * <p>El refresh token es un JWT firmado que transporta el tenant y un secreto
 * aleatorio; en la base solo se guarda el SHA-256 de ese secreto. El tenant
 * firmado es lo que permite fijar {@link TenantContext} ANTES de abrir la
 * transaccion (regla de {@code TenantAwareTransactionManager.doBegin}) aun
 * cuando el access token ya expiro. Si alguien alterara el tenant, la firma
 * no validaria; y aun con una firma valida, RLS no revelaria la fila de otro
 * restaurante.
 *
 * <p>Las transacciones se abren con {@link TransactionTemplate} por la misma
 * razon que en {@link AuthService}: el tenant se decide dentro del metodo.
 */
@Service
public class RefreshTokenService {

    /** Resultado de emitir o rotar: el par de tokens y la expiracion de la sesion. */
    public record IssuedTokens(User user, String accessToken, String refreshToken,
                               Instant sessionExpiresAt) {
    }

    private sealed interface Outcome permits Rotated, Rejected {
    }

    private record Rotated(IssuedTokens tokens) implements Outcome {
    }

    private record Rejected() implements Outcome {
    }

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int SECRET_BYTES = 32;

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final JwtService jwtService;
    private final TransactionTemplate transactionTemplate;

    public RefreshTokenService(
            RefreshTokenRepository refreshTokenRepository,
            UserRepository userRepository,
            TenantRepository tenantRepository,
            JwtService jwtService,
            TransactionTemplate transactionTemplate) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.userRepository = userRepository;
        this.tenantRepository = tenantRepository;
        this.jwtService = jwtService;
        this.transactionTemplate = transactionTemplate;
    }

    /**
     * Abre una familia nueva tras un login. El llamador debe haber fijado el
     * tenant del usuario en {@link TenantContext}.
     *
     * <p>La sesion tiene una expiracion absoluta: los tokens rotados heredan
     * la del login, asi que renovar no alarga la sesion indefinidamente.
     */
    public IssuedTokens issueForLogin(User user) {
        return transactionTemplate.execute(status -> {
            Instant now = Instant.now();
            refreshTokenRepository.deleteExpiredForUser(user.getId(), now);

            Instant sessionExpiresAt = now.plus(jwtService.refreshTokenTtl());
            String refreshToken = persistNewToken(user, UUID.randomUUID(), sessionExpiresAt).raw();

            return new IssuedTokens(user, jwtService.generateAccessToken(user),
                    refreshToken, sessionExpiresAt);
        });
    }

    /**
     * Consume el refresh token presentado y emite un par nuevo de la misma
     * familia. Cualquier rechazo termina en {@link BadCredentialsException}
     * sin detalle, para no revelar si el token era desconocido, expirado o
     * reutilizado.
     *
     * <p>Si el token ya habia sido consumido, la familia completa se revoca y
     * esa revocacion se confirma antes de lanzar la excepcion: por eso la
     * transaccion devuelve un resultado en vez de lanzar desde dentro, lo que
     * provocaria un rollback que desharia la revocacion.
     */
    public IssuedTokens rotate(String rawRefreshToken) {
        JwtService.RefreshClaims claims = parseOrReject(rawRefreshToken);

        Outcome outcome = withTenant(claims.tenantId(),
                () -> transactionTemplate.execute(status -> rotateInTransaction(claims)));

        if (outcome instanceof Rotated rotated) {
            return rotated.tokens();
        }
        throw new BadCredentialsException("Sesion no renovable.");
    }

    /**
     * Revoca la familia completa del token presentado. Un token invalido o
     * desconocido no es un error: el logout debe dejar al cliente sin sesion
     * en cualquier caso.
     */
    public void revokeSession(String rawRefreshToken) {
        JwtService.RefreshClaims claims;
        try {
            claims = jwtService.parseRefreshToken(rawRefreshToken);
        } catch (RuntimeException invalidToken) {
            return;
        }

        withTenant(claims.tenantId(), () -> transactionTemplate.execute(status -> {
            findOwnedToken(claims).ifPresent(token ->
                    refreshTokenRepository.revokeFamily(token.getFamilyId(), Instant.now()));
            return null;
        }));
    }

    private Outcome rotateInTransaction(JwtService.RefreshClaims claims) {
        Instant now = Instant.now();

        // Un token de otro tenant queda oculto por RLS y cae aqui como
        // desconocido: ni se lee ni se modifica.
        Optional<RefreshToken> found = findOwnedToken(claims);
        if (found.isEmpty()) {
            return new Rejected();
        }
        RefreshToken token = found.get();

        if (token.getRevokedAt() != null) {
            return revokeFamilyOnReuse(token, now);
        }
        if (!token.getExpiresAt().isAfter(now)) {
            return new Rejected();
        }
        if (refreshTokenRepository.consume(token.getId(), now) == 0) {
            // Otra peticion lo consumio entre la lectura y este UPDATE.
            return revokeFamilyOnReuse(token, now);
        }

        // Renovar exige lo mismo que el login: usuario y restaurante activos.
        // Sin esto, desactivar cualquiera de los dos dejaria de cortar el
        // acceso a los 15 minutos y la sesion seguiria viva hasta su expiracion.
        Optional<User> user = userRepository.findById(token.getUserId()).filter(User::isActive);
        boolean tenantActive = tenantRepository.findById(token.getTenantId())
                .map(Tenant::isActive)
                .orElse(false);
        if (user.isEmpty() || !tenantActive) {
            refreshTokenRepository.revokeFamily(token.getFamilyId(), now);
            return new Rejected();
        }

        NewToken next = persistNewToken(user.get(), token.getFamilyId(), token.getExpiresAt());
        refreshTokenRepository.markReplaced(token.getId(), next.id(), now);

        return new Rotated(new IssuedTokens(user.get(),
                jwtService.generateAccessToken(user.get()), next.raw(), token.getExpiresAt()));
    }

    private Outcome revokeFamilyOnReuse(RefreshToken token, Instant now) {
        int revoked = refreshTokenRepository.revokeFamily(token.getFamilyId(), now);
        log.warn("Reutilizacion de refresh token detectada: familia {} del usuario {} revocada ({} tokens vigentes).",
                token.getFamilyId(), token.getUserId(), revoked);
        return new Rejected();
    }

    private Optional<RefreshToken> findOwnedToken(JwtService.RefreshClaims claims) {
        return refreshTokenRepository.findByTokenHash(hash(claims.secret()))
                .filter(token -> token.getUserId().equals(claims.userId()));
    }

    private record NewToken(UUID id, String raw) {
    }

    private NewToken persistNewToken(User user, UUID familyId, Instant expiresAt) {
        String secret = newSecret();
        RefreshToken saved = refreshTokenRepository.save(RefreshToken.builder()
                .userId(user.getId())
                .tokenHash(hash(secret))
                .familyId(familyId)
                .expiresAt(expiresAt)
                .build());

        String raw = jwtService.generateRefreshToken(
                user.getId(), user.getTenantId(), secret, expiresAt);
        return new NewToken(saved.getId(), raw);
    }

    private JwtService.RefreshClaims parseOrReject(String rawRefreshToken) {
        try {
            return jwtService.parseRefreshToken(rawRefreshToken);
        } catch (RuntimeException invalidToken) {
            throw new BadCredentialsException("Sesion no renovable.");
        }
    }

    /**
     * Fija el tenant firmado del refresh token durante la operacion y deja el
     * contexto como estaba: la peticion pudo traer un access token (incluso de
     * otro restaurante) que {@code TenantFilter} ya habia publicado.
     */
    private static <T> T withTenant(UUID tenantId, Supplier<T> action) {
        UUID previous = TenantContext.currentOrNull();
        TenantContext.set(tenantId);
        try {
            return action.get();
        } finally {
            if (previous == null) {
                TenantContext.clear();
            } else {
                TenantContext.set(previous);
            }
        }
    }

    private static String newSecret() {
        byte[] bytes = new byte[SECRET_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String hash(String secret) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(secret.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 no disponible.", impossible);
        }
    }
}
