package com.callejon9.sale.web;

import com.callejon9.sale.service.SaleHistoryService;
import com.callejon9.sale.web.dto.SalesHistoryResponse;
import com.callejon9.shared.web.PageQuery;
import java.time.LocalDate;
import java.util.Map;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** El historial de ventas es de quien cobra y de quien administra el restaurante. */
@PreAuthorize("hasAnyRole('CASHIER','ADMIN')")
@RestController
@RequestMapping("/api/v1/sales")
public class SalesController {

    private final SaleHistoryService saleHistoryService;

    public SalesController(SaleHistoryService saleHistoryService) {
        this.saleHistoryService = saleHistoryService;
    }

    private static final Map<String, String> SORTABLE = Map.of("createdAt", "createdAt", "total", "total");

    /**
     * {@code page}, {@code size} (1 a {@value PageQuery#MAX_SIZE}) y
     * {@code sort} sobre {@code createdAt} o {@code total}; por defecto, la
     * venta mas reciente primero.
     */
    @GetMapping
    public SalesHistoryResponse history(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        Pageable pageable = PageQuery.of(page, size, sort, SORTABLE, Sort.by(Sort.Direction.DESC, "createdAt"));
        return saleHistoryService.getHistory(from, to, pageable);
    }
}
