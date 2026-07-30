package com.callejon9.table.repository;

import com.callejon9.table.domain.RestaurantTable;
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
public interface RestaurantTableRepository extends JpaRepository<RestaurantTable, UUID> {

    boolean existsByNumber(int number);

    List<RestaurantTable> findByActiveTrueOrderByNumber();

    /**
     * Igual que {@code findById}, pero con {@code SELECT ... FOR UPDATE}: bloquea
     * la fila hasta que la transaccion actual termine. Sin este lock, dos
     * peticiones que ocupan la misma mesa bajo READ COMMITTED pueden leer
     * ambas el estado FREE antes de que cualquiera escriba, y las dos crean
     * una orden (doble reservacion silenciosa, sin ningun error). Con el
     * lock, la segunda transaccion espera a que la primera confirme y
     * entonces relee el estado ya actualizado.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from RestaurantTable t where t.id = :id")
    Optional<RestaurantTable> findByIdForUpdate(@Param("id") UUID id);
}
