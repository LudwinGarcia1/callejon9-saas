package com.callejon9.analytics.service;

import com.callejon9.analytics.repository.AnalyticsRepository;
import com.callejon9.analytics.web.dto.AnalyticsResponse;
import com.callejon9.shared.time.BusinessCalendar;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("UT · AnalyticsService")
class AnalyticsServiceTest {

    @Mock
    private AnalyticsRepository analyticsRepository;

    private BusinessCalendar businessCalendar;
    private AnalyticsService analyticsService;

    @BeforeEach
    void setUp() {
        businessCalendar = new BusinessCalendar("America/Mexico_City");
        analyticsService = new AnalyticsService(analyticsRepository, businessCalendar);
    }

    @Test
    @DisplayName("Periodo sin ventas retorna respuesta con listas vacías y totales en cero sin errores")
    void periodWithNoSalesReturnsEmptyResponseWithoutErrors() {
        LocalDate from = LocalDate.of(2026, 10, 1);
        LocalDate to = LocalDate.of(2026, 10, 7);

        when(analyticsRepository.productRevenue(any(), any())).thenReturn(List.of());
        when(analyticsRepository.dailySales(any(), any(), any())).thenReturn(List.of());
        when(analyticsRepository.paymentMix(any(), any())).thenReturn(List.of());

        AnalyticsResponse response = analyticsService.getAnalytics(from, to);

        assertThat(response).isNotNull();
        assertThat(response.pareto()).isEmpty();
        assertThat(response.salesByDay()).hasSize(7); // 7 días generados con cero
        assertThat(response.salesByDay()).allMatch(row -> row.total().compareTo(BigDecimal.ZERO) == 0);
        assertThat(response.salesByDay()).allMatch(row -> row.count() == 0);
        assertThat(response.paymentMix()).isEmpty();
    }
}
