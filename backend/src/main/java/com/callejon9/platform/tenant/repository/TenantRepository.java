package com.callejon9.platform.tenant.repository;

import com.callejon9.platform.tenant.domain.Tenant;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TenantRepository extends JpaRepository<Tenant, UUID> {

    Optional<Tenant> findBySlug(String slug);

    boolean existsBySlug(String slug);

    /**
     * Igual que {@code findById}, pero con {@code SELECT ... FOR UPDATE}: serializa
     * operaciones "leer conteo, decidir, escribir" que corren sobre el mismo tenant
     * (ver {@code UserService.setActive} y {@code PlanLimitService.assertCanAddUser}).
     * Sin este lock, dos peticiones concurrentes leen ambas el conteo previo a
     * cualquier escritura y las dos pasan la validacion, aunque juntas la violen.
     * {@code tenants} no tiene RLS -- es el catalogo de control-plane -- asi que
     * bloquear esta fila no requiere ningun tenant activo en {@link
     * com.callejon9.tenancy.TenantContext}.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Tenant t where t.id = :id")
    Optional<Tenant> findByIdForUpdate(@Param("id") UUID id);
}
