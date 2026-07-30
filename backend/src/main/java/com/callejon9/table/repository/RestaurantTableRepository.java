package com.callejon9.table.repository;

import com.callejon9.table.domain.RestaurantTable;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * No filtra por tenant en las consultas: las politicas RLS de PostgreSQL ya
 * limitan las filas visibles al tenant activo.
 */
public interface RestaurantTableRepository extends JpaRepository<RestaurantTable, UUID> {

    boolean existsByNumber(int number);

    List<RestaurantTable> findByActiveTrueOrderByNumber();
}
