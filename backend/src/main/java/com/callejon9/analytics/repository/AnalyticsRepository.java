package com.callejon9.analytics.repository;

import com.callejon9.analytics.web.dto.PaymentMixAggregate;
import com.callejon9.analytics.web.dto.ProductRevenueAggregate;
import com.callejon9.sale.domain.Sale;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Agregaciones para la pantalla de analitica. Todas las sumas y conteos se
 * calculan en la base de datos, nunca sumando listas en Java.
 *
 * No filtra por tenant en las consultas: las politicas RLS de PostgreSQL ya
 * limitan las filas visibles al tenant activo (mismo criterio que
 * {@code SaleRepository}).
 *
 * Solo se consideran ventas COMPLETED: es la contraparte exacta de una orden
 * PAID -- {@code CheckoutService} crea ambas en la misma transaccion -- y ya
 * es el filtro que usa el historial de ventas para el mismo concepto.
 */
public interface AnalyticsRepository extends JpaRepository<Sale, UUID> {

    /**
     * Ingreso por producto ({@code unit_price * quantity} sumado sobre
     * {@code order_items}), de mayor a menor. {@code OrderItem} no tiene
     * asociacion JPA mapeada desde {@code Sale} (solo comparten el id de la
     * orden como columna simple), asi que el join se declara explicitamente
     * con {@code on}, igual que en {@code SaleRepository.findHistory}.
     */
    @Query("""
            select new com.callejon9.analytics.web.dto.ProductRevenueAggregate(
                oi.productName, sum(oi.unitPrice * oi.quantity))
            from Sale s
            join com.callejon9.order.domain.OrderItem oi on oi.orderId = s.orderId
            where s.status = com.callejon9.sale.domain.SaleStatus.COMPLETED
              and s.createdAt >= :from and s.createdAt < :to
            group by oi.productName
            order by sum(oi.unitPrice * oi.quantity) desc
            """)
    List<ProductRevenueAggregate> productRevenue(@Param("from") Instant from, @Param("to") Instant to);

    /** Ingreso total del rango, el mismo universo que {@link #productRevenue}, para calcular los porcentajes del Pareto. */
    @Query("""
            select coalesce(sum(oi.unitPrice * oi.quantity), 0)
            from Sale s
            join com.callejon9.order.domain.OrderItem oi on oi.orderId = s.orderId
            where s.status = com.callejon9.sale.domain.SaleStatus.COMPLETED
              and s.createdAt >= :from and s.createdAt < :to
            """)
    BigDecimal totalProductRevenue(@Param("from") Instant from, @Param("to") Instant to);

    /** Numero de ventas e ingreso por metodo de pago en el rango. */
    @Query("""
            select new com.callejon9.analytics.web.dto.PaymentMixAggregate(
                s.paymentMethod, count(s), coalesce(sum(s.total), 0))
            from Sale s
            where s.status = com.callejon9.sale.domain.SaleStatus.COMPLETED
              and s.createdAt >= :from and s.createdAt < :to
            group by s.paymentMethod
            order by sum(s.total) desc
            """)
    List<PaymentMixAggregate> paymentMix(@Param("from") Instant from, @Param("to") Instant to);

    /**
     * Ventas por dia DEL NEGOCIO, no del dia UTC: {@code created_at at time
     * zone :timezone} convierte el instante a la hora de pared de esa zona
     * antes de truncarlo a fecha, la misma correccion que
     * {@code BusinessCalendar} aplica en Java para el rango completo.
     *
     * Nativa porque JPQL no tiene una forma portable de expresar
     * {@code AT TIME ZONE}; solo trae los dias CON al menos una venta -- el
     * relleno de dias vacios con cero pasa en el servicio, que ya conoce los
     * limites exactos del rango solicitado.
     */
    @Query(value = """
            select (s.created_at at time zone cast(:timezone as text))::date as day,
                   count(*) as cnt,
                   coalesce(sum(s.total), 0) as total
            from sales s
            where s.status = 'COMPLETED'
              and s.created_at >= :from
              and s.created_at < :to
            group by 1
            order by 1
            """, nativeQuery = true)
    List<DailySalesProjection> dailySales(
            @Param("from") Instant from, @Param("to") Instant to, @Param("timezone") String timezone);
}
