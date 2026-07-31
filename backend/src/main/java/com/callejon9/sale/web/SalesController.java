package com.callejon9.sale.web;

import com.callejon9.sale.service.SaleHistoryService;
import com.callejon9.sale.web.dto.SalesHistoryResponse;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Cualquier usuario autenticado puede consultar el historial (ver SecurityConfig: anyRequest().authenticated()). */
@RestController
@RequestMapping("/api/v1/sales")
public class SalesController {

    private final SaleHistoryService saleHistoryService;

    public SalesController(SaleHistoryService saleHistoryService) {
        this.saleHistoryService = saleHistoryService;
    }

    @GetMapping
    public SalesHistoryResponse history(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return saleHistoryService.getHistory(from, to);
    }
}
