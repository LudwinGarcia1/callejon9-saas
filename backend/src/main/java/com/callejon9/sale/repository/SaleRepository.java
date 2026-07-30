package com.callejon9.sale.repository;

import com.callejon9.sale.domain.Sale;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * No filtra por tenant en las consultas: las politicas RLS de PostgreSQL ya
 * limitan las filas visibles al tenant activo.
 */
public interface SaleRepository extends JpaRepository<Sale, UUID> {
}
