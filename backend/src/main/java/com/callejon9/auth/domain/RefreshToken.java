package com.callejon9.auth.domain;

import com.callejon9.shared.domain.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "refresh_tokens")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefreshToken extends TenantScopedEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /** Se guarda el hash, nunca el token en claro. */
    @Column(name = "token_hash", nullable = false, unique = true, length = 100)
    private String tokenHash;

    /**
     * Todos los tokens que descienden de un mismo login comparten familia.
     * Reutilizar un token ya consumido revoca la familia completa.
     */
    @Column(name = "family_id", nullable = false, updatable = false)
    private UUID familyId;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    /** Token que sustituyo a este al rotar; null si no se ha consumido. */
    @Column(name = "replaced_by")
    private UUID replacedBy;
}
