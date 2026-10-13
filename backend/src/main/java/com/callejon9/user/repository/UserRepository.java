package com.callejon9.user.repository;

import com.callejon9.user.domain.User;
import com.callejon9.user.domain.UserRole;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * No hace falta filtrar por tenant en las consultas: las politicas RLS de
 * PostgreSQL ya limitan las filas visibles al tenant activo. Es precisamente
 * la ventaja de mover el aislamiento al motor.
 */
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    long countByTenantId(UUID tenantId);

    long countByRoleAndActiveTrue(UserRole role);

    /**
     * Igual que {@code findById}, pero con {@code SELECT ... FOR SHARE}: la
     * renovacion de sesion lo usa para ordenarse con la desactivacion del
     * usuario, que escribe esa fila. Ver
     * {@code RefreshTokenService.rotateInTransaction}.
     */
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("SELECT u FROM User u WHERE u.id = :id")
    Optional<User> findByIdForShare(@Param("id") UUID id);
}
