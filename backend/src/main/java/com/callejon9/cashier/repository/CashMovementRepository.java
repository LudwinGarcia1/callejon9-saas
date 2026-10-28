package com.callejon9.cashier.repository;

import com.callejon9.cashier.domain.CashMovement;
import com.callejon9.cashier.domain.CashMovementType;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CashMovementRepository extends JpaRepository<CashMovement, UUID> {

    List<CashMovement> findAllByCashSessionIdOrderByCreatedAtDesc(UUID cashSessionId);

    @Query("""
            select coalesce(sum(movement.amount), 0)
            from CashMovement movement
            where movement.cashSessionId = :cashSessionId and movement.movementType = :movementType
            """)
    BigDecimal sumAmountByCashSessionIdAndMovementType(
            @Param("cashSessionId") UUID cashSessionId,
            @Param("movementType") CashMovementType movementType);
}
