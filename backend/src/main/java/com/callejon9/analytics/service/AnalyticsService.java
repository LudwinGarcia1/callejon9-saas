package com.callejon9.analytics.service;

import com.callejon9.analytics.repository.AnalyticsRepository;
import com.callejon9.analytics.repository.DailySalesProjection;
import com.callejon9.analytics.web.dto.AnalyticsResponse;
import com.callejon9.analytics.web.dto.PaymentMixAggregate;
import com.callejon9.analytics.web.dto.PaymentMixRow;
import com.callejon9.analytics.web.dto.ParetoRow;
import com.callejon9.analytics.web.dto.ProductRevenueAggregate;
import com.callejon9.analytics.web.dto.SalesByDayRow;
import com.callejon9.shared.time.BusinessCalendar;
import com.callejon9.shared.time.InstantRange;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Calcula los tres conjuntos de datos de la pantalla de analitica: Pareto de
 * productos, ventas por dia y mezcla de metodos de pago.
 *
 * El rango se resuelve en la zona horaria del negocio (ver
 * {@link BusinessCalendar}), igual que el historial de ventas. Sin parametros
 * el rango por defecto son los ultimos 7 dias terminando hoy; con solo uno de
 * los dos limites, el otro se completa relativo a ese limite (siete dias
 * antes de {@code to}, u hoy si falta {@code to}).
 *
 * Los porcentajes de las tres respuestas viajan en escala 0-100, nunca 0-1.
 */
@Service
public class AnalyticsService {

    /** Productos que se muestran individualmente en el Pareto; el resto se pliega en "Otros". */
    static final int TOP_PRODUCTS_LIMIT = 12;
    static final String OTHERS_LABEL = "Otros";

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final int SHARE_SCALE = 2;

    private final AnalyticsRepository analyticsRepository;
    private final BusinessCalendar businessCalendar;

    public AnalyticsService(AnalyticsRepository analyticsRepository, BusinessCalendar businessCalendar) {
        this.analyticsRepository = analyticsRepository;
        this.businessCalendar = businessCalendar;
    }

    @Transactional(readOnly = true)
    public AnalyticsResponse getAnalytics(LocalDate from, LocalDate to) {
        LocalDate today = businessCalendar.today();
        LocalDate effectiveTo = to != null ? to : today;
        LocalDate effectiveFrom = from != null ? from : effectiveTo.minusDays(6);

        InstantRange range = businessCalendar.toInstantRange(effectiveFrom, effectiveTo);

        return new AnalyticsResponse(
                buildPareto(range),
                buildSalesByDay(range, effectiveFrom, effectiveTo),
                buildPaymentMix(range));
    }

    /**
     * Cada porcentaje se redondea a 2 decimales, salvo el ultimo renglon: su
     * {@code revenueShare} se calcula como el residuo (100 menos lo ya
     * acumulado) en vez de dividir de nuevo, para que la suma de las
     * columnas cierre exactamente en 100.00 pese al redondeo de las demas
     * filas, y su {@code cumulativeShare} sea exactamente 100.00.
     */
    private List<ParetoRow> buildPareto(InstantRange range) {
        List<ProductRevenueAggregate> aggregates =
                analyticsRepository.productRevenue(range.start(), range.endExclusive());
        if (aggregates.isEmpty()) {
            return List.of();
        }

        BigDecimal totalRevenue = analyticsRepository.totalProductRevenue(range.start(), range.endExclusive());
        if (totalRevenue == null || totalRevenue.signum() == 0) {
            return List.of();
        }

        List<ProductRevenueAggregate> rows = foldIntoTopAndOthers(aggregates);

        List<ParetoRow> result = new ArrayList<>(rows.size());
        BigDecimal cumulative = BigDecimal.ZERO;
        for (int i = 0; i < rows.size(); i++) {
            ProductRevenueAggregate row = rows.get(i);
            boolean isLast = i == rows.size() - 1;

            BigDecimal share = isLast
                    ? HUNDRED.subtract(cumulative)
                    : row.revenue().multiply(HUNDRED).divide(totalRevenue, SHARE_SCALE, RoundingMode.HALF_UP);
            cumulative = isLast ? HUNDRED.setScale(SHARE_SCALE, RoundingMode.HALF_UP) : cumulative.add(share);

            result.add(new ParetoRow(row.productName(), row.revenue(), share, cumulative));
        }
        return result;
    }

    /** Conserva los {@value #TOP_PRODUCTS_LIMIT} productos de mayor ingreso; el resto se suma en una fila "Otros". */
    private List<ProductRevenueAggregate> foldIntoTopAndOthers(List<ProductRevenueAggregate> aggregates) {
        if (aggregates.size() <= TOP_PRODUCTS_LIMIT) {
            return aggregates;
        }

        List<ProductRevenueAggregate> top = aggregates.subList(0, TOP_PRODUCTS_LIMIT);
        List<ProductRevenueAggregate> rest = aggregates.subList(TOP_PRODUCTS_LIMIT, aggregates.size());

        BigDecimal othersRevenue = rest.stream()
                .map(ProductRevenueAggregate::revenue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<ProductRevenueAggregate> folded = new ArrayList<>(top);
        folded.add(new ProductRevenueAggregate(OTHERS_LABEL, othersRevenue));
        return folded;
    }

    /** Un renglon por dia del rango solicitado, incluyendo los que no tuvieron ninguna venta. */
    private List<SalesByDayRow> buildSalesByDay(InstantRange range, LocalDate from, LocalDate to) {
        List<DailySalesProjection> daysWithSales =
                analyticsRepository.dailySales(range.start(), range.endExclusive(), businessCalendar.zone().getId());

        Map<LocalDate, DailySalesProjection> byDay = new HashMap<>();
        for (DailySalesProjection row : daysWithSales) {
            byDay.put(row.getDay(), row);
        }

        List<SalesByDayRow> result = new ArrayList<>();
        for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
            DailySalesProjection row = byDay.get(day);
            BigDecimal total = row != null ? row.getTotal() : BigDecimal.ZERO.setScale(2, RoundingMode.UNNECESSARY);
            long count = row != null ? row.getCnt() : 0L;
            result.add(new SalesByDayRow(day, total, count));
        }
        return result;
    }

    private List<PaymentMixRow> buildPaymentMix(InstantRange range) {
        List<PaymentMixAggregate> aggregates = analyticsRepository.paymentMix(range.start(), range.endExclusive());
        if (aggregates.isEmpty()) {
            return List.of();
        }

        BigDecimal grandTotal = aggregates.stream()
                .map(PaymentMixAggregate::total)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<PaymentMixRow> result = new ArrayList<>(aggregates.size());
        for (PaymentMixAggregate aggregate : aggregates) {
            BigDecimal share = grandTotal.signum() == 0
                    ? BigDecimal.ZERO.setScale(SHARE_SCALE, RoundingMode.UNNECESSARY)
                    : aggregate.total().multiply(HUNDRED).divide(grandTotal, SHARE_SCALE, RoundingMode.HALF_UP);
            result.add(new PaymentMixRow(aggregate.method(), aggregate.count(), aggregate.total(), share));
        }
        return result;
    }
}
