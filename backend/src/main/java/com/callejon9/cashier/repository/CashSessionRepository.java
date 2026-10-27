package com.callejon9.cashier.repository;

import com.callejon9.cashier.domain.CashSession;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CashSessionRepository extends JpaRepository<CashSession, UUID> {

    List<CashSession> findAllByOrderByOpenedAtDesc();

    List<CashSession> findAllByCashierIdOrderByOpenedAtDesc(UUID cashierId);

    Optional<CashSession> findByCashierIdAndClosedAtIsNull(UUID cashierId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select session from CashSession session where session.cashierId = :cashierId and session.closedAt is null")
    Optional<CashSession> findOpenForCashierForUpdate(@Param("cashierId") UUID cashierId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select session from CashSession session where session.id = :id")
    Optional<CashSession> findByIdForUpdate(@Param("id") UUID id);
}
