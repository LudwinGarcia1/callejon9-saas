package com.callejon9.shared.web;

import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

/**
 * Contrato comun de los listados paginados (CAL-16). {@code page} empieza en
 * 0. Se publica este record en vez de {@link Page} porque la serializacion de
 * {@code PageImpl} expone detalles internos de Spring Data y no es estable
 * entre versiones.
 */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public static <T, R> PageResponse<R> from(Page<T> page, Function<T, R> mapper) {
        return new PageResponse<>(page.getContent().stream().map(mapper).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

    public static <T> PageResponse<T> from(Page<T> page) {
        return from(page, Function.identity());
    }
}
