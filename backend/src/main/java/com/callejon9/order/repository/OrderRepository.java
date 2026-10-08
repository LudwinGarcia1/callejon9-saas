package com.callejon9.order.repository;

import com.callejon9.order.domain.Order;
import com.callejon9.order.domain.OrderStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select o from Order o where o.id = :id")
    java.util.Optional<Order> findForCheckout(@org.springframework.data.repository.query.Param("id") UUID id);

    List<Order> findAllByOrderByOpenedAtDesc();

    List<Order> findByStatusOrderByOpenedAtDesc(OrderStatus status);

    /** Tablero de cocina: las ordenes enviadas, la mas antigua primero. */
    List<Order> findByStatusOrderBySentToKitchenAtAsc(OrderStatus status);
}
