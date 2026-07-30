package com.callejon9.user.repository;

import com.callejon9.user.domain.User;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * No hace falta filtrar por tenant en las consultas: las politicas RLS de
 * PostgreSQL ya limitan las filas visibles al tenant activo. Es precisamente
 * la ventaja de mover el aislamiento al motor.
 */
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    long countByTenantId(UUID tenantId);
}
