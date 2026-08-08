package com.callejon9.inventory.repository;

import com.callejon9.inventory.domain.InventoryItem;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * No filtra por tenant en las consultas: las politicas RLS de PostgreSQL ya
 * limitan las filas visibles al tenant activo.
 */
public interface InventoryItemRepository extends JpaRepository<InventoryItem, UUID> {

    boolean existsByName(String name);

    /** Para renombrar un insumo existente sin chocar contra si mismo. */
    boolean existsByNameAndIdNot(String name, UUID id);

    List<InventoryItem> findByActiveTrueOrderByName();

    /** Para includeInactive=true: todos los insumos, sin filtrar por estado. */
    List<InventoryItem> findAllByOrderByName();

    /**
     * Igual que {@code findById}, pero con {@code SELECT ... FOR UPDATE}:
     * bloquea la fila hasta que la transaccion actual termine.
     *
     * Sin este lock, dos movimientos simultaneos sobre el mismo insumo bajo
     * READ COMMITTED leen el mismo stock, calculan sobre el mismo valor y una
     * escritura sobrescribe a la otra: el ledger guarda dos filas y la columna
     * stock refleja una. Con el lock, la segunda transaccion espera a que la
     * primera confirme y relee el stock ya actualizado.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from InventoryItem i where i.id = :id")
    Optional<InventoryItem> findByIdForUpdate(@Param("id") UUID id);
}
