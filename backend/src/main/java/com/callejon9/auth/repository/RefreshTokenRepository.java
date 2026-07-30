package com.callejon9.auth.repository;

import com.callejon9.auth.domain.RefreshToken;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Cablea el acceso a refresh_tokens (tabla protegida por RLS). La rotacion de
 * tokens se implementa en el Plan 2; por ahora ningun flujo inserta filas
 * aqui, para no dejar un camino que escriba sin tenant fijado en contexto.
 */
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);
}
