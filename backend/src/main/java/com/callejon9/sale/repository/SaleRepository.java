package com.callejon9.sale.repository;

import com.callejon9.sale.domain.Sale;
import com.callejon9.sale.web.dto.SaleHistoryRow;
import com.callejon9.sale.web.dto.SaleHistorySummary;
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
public interface SaleRepository extends JpaRepository<Sale, UUID> {

    /**
     * Historial de ventas para el rango [from, to) solicitado, mas reciente
     * primero. {@code Order}, {@code RestaurantTable}, {@code User} y
     * {@code Ticket} no tienen asociacion JPA mapeada desde {@code Sale}
     * (solo comparten el id como columna simple), asi que el join se declara
     * explicitamente con {@code on}; los left join toleran una mesa borrada,
     * un cajero dado de baja o un ticket que aun no exista.
     * Solo se cuentan ventas COMPLETED: es lo unico que el checkout genera
     * hoy, pero filtrar explicitamente evita que un futuro estado PENDING o
     * CANCELED aparezca en un historial que se supone son cobros reales.
     */
    @Query("""
            select new com.callejon9.sale.web.dto.SaleHistoryRow(
                s.id, o.folio, t.number, u.fullName, tk.id,
                s.paymentMethod, s.subtotal, s.tip, s.total, s.createdAt)
            from Sale s
            join com.callejon9.order.domain.Order o on o.id = s.orderId
            left join com.callejon9.table.domain.RestaurantTable t on t.id = s.tableId
            left join com.callejon9.user.domain.User u on u.id = s.cashierId
            left join com.callejon9.ticket.domain.Ticket tk on tk.saleId = s.id
            where s.status = com.callejon9.sale.domain.SaleStatus.COMPLETED
              and s.createdAt >= :from and s.createdAt < :to
            order by s.createdAt desc
            """)
    List<SaleHistoryRow> findHistory(@Param("from") Instant from, @Param("to") Instant to);

    /** Numero de ventas y suma de sus totales para el mismo rango, calculado en la base de datos. */
    @Query("""
            select new com.callejon9.sale.web.dto.SaleHistorySummary(count(s), coalesce(sum(s.total), 0))
            from Sale s
            where s.status = com.callejon9.sale.domain.SaleStatus.COMPLETED
              and s.createdAt >= :from and s.createdAt < :to
            """)
    SaleHistorySummary summarize(@Param("from") Instant from, @Param("to") Instant to);
}
