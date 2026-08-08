package com.callejon9.inventory.repository;

import com.callejon9.inventory.domain.InventoryItem;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
