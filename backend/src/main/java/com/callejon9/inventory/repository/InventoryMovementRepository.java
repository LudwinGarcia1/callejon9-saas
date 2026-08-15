package com.callejon9.inventory.repository;

import com.callejon9.inventory.domain.InventoryMovement;
import com.callejon9.inventory.web.dto.InventoryMovementRow;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * No filtra por tenant en las consultas: las politicas RLS de PostgreSQL ya
 * limitan las filas visibles al tenant activo.
 */
public interface InventoryMovementRepository extends JpaRepository<InventoryMovement, UUID> {

    /** Para bloquear el cambio de unidad en cuanto el insumo tiene historia. */
    boolean existsByInventoryItemId(UUID inventoryItemId);

    /**
     * Ledger del rango [from, to), mas reciente primero, opcionalmente de un
     * solo insumo. El join a InventoryItem es interno porque la llave es NOT
     * NULL con ON DELETE CASCADE: un movimiento sin insumo no existe. El de
     * User es left join porque su llave es ON DELETE SET NULL y el usuario
     * puede estar dado de baja. Ninguna de las dos asociaciones esta mapeada
     * en JPA (solo comparten el id como columna simple), asi que el join se
     * declara con {@code on}.
     */
    @Query("""
            select new com.callejon9.inventory.web.dto.InventoryMovementRow(
                m.id, m.inventoryItemId, i.name, i.unit,
                m.movementType, m.quantity, m.reason, u.fullName, m.createdAt)
            from InventoryMovement m
            join com.callejon9.inventory.domain.InventoryItem i on i.id = m.inventoryItemId
            left join com.callejon9.user.domain.User u on u.id = m.userId
            where m.createdAt >= :from and m.createdAt < :to
              and (:itemId is null or m.inventoryItemId = :itemId)
            order by m.createdAt desc
            """)
    List<InventoryMovementRow> findHistory(@Param("from") Instant from,
                                           @Param("to") Instant to,
                                           @Param("itemId") UUID itemId);

    /**
     * Los movimientos de un insumo, para reconstruir su stock desde el ledger.
     *
     * Devuelve las filas y no una suma en SQL a proposito: la columna quantity
     * guarda la cantidad SIN signo salvo en ADJUSTMENT, y quien conoce el signo
     * de cada tipo es {@link com.callejon9.inventory.domain.InventoryMovementType}.
     * Un {@code sum(quantity)} sumaria una salida como si fuera una entrada, y
     * reproducir ese signo en un CASE de JPQL duplicaria en SQL una regla que
     * ya vive en el enum.
     */
    List<InventoryMovement> findByInventoryItemId(UUID inventoryItemId);
}
