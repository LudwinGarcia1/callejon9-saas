package com.callejon9.order.repository;

import com.callejon9.order.domain.Order;
import com.callejon9.order.domain.OrderStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.id = :id")
    Optional<Order> findForCheckout(@Param("id") UUID id);

    List<Order> findAllByOrderByOpenedAtDesc();

    List<Order> findByStatusOrderByOpenedAtDesc(OrderStatus status);

    /** Tablero de cocina: las ordenes enviadas, la mas antigua primero. */
    List<Order> findByStatusOrderBySentToKitchenAtAsc(OrderStatus status);

    Optional<Order> findFirstByTableIdAndStatusInOrderByOpenedAtDesc(UUID tableId, Collection<OrderStatus> statuses);
}
