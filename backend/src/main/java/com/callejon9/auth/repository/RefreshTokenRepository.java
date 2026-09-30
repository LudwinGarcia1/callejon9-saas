package com.callejon9.auth.repository;

import com.callejon9.auth.domain.RefreshToken;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Acceso a refresh_tokens (tabla protegida por RLS). Todas las operaciones
 * deben correr en una transaccion abierta con el tenant ya fijado en
 * {@link com.callejon9.tenancy.TenantContext}; sin el, la politica no revela
 * ni deja modificar ninguna fila.
 */
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * Consume el token de forma atomica. La condicion {@code revoked_at IS
     * NULL} es la que resuelve la carrera: si dos peticiones presentan el
     * mismo token, PostgreSQL serializa ambos UPDATE sobre la fila y el
     * segundo, al reevaluar la condicion tras el commit del primero, no
     * afecta ninguna fila. Funciona igual con varias instancias del backend
     * porque el candado vive en la base, no en memoria.
     *
     * @return 1 si esta peticion consumio el token, 0 si otra ya lo habia hecho
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE RefreshToken t
               SET t.revokedAt = :now, t.updatedAt = :now
             WHERE t.id = :id AND t.revokedAt IS NULL
            """)
    int consume(@Param("id") UUID id, @Param("now") Instant now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE RefreshToken t
               SET t.replacedBy = :replacedBy, t.updatedAt = :now
             WHERE t.id = :id
            """)
    void markReplaced(@Param("id") UUID id, @Param("replacedBy") UUID replacedBy,
                      @Param("now") Instant now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE RefreshToken t
               SET t.revokedAt = :now, t.updatedAt = :now
             WHERE t.familyId = :familyId AND t.revokedAt IS NULL
            """)
    int revokeFamily(@Param("familyId") UUID familyId, @Param("now") Instant now);

    /**
     * Limpieza oportunista: se invoca en cada login y solo toca las filas del
     * propio usuario dentro de su tenant, asi que no necesita un job con
     * privilegios que se salte RLS. Las filas revocadas pero aun vigentes se
     * conservan a proposito: son las que permiten detectar una reutilizacion.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM RefreshToken t WHERE t.userId = :userId AND t.expiresAt < :now")
    int deleteExpiredForUser(@Param("userId") UUID userId, @Param("now") Instant now);
}
