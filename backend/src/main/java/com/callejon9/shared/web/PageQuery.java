package com.callejon9.shared.web;

import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Convierte {@code page}, {@code size} y {@code sort} de la peticion en un
 * {@link Pageable} acotado. No se usa el resolver de Spring Data porque
 * recorta en silencio un {@code size} excesivo y acepta ordenar por cualquier
 * propiedad de la entidad; aqui ambos casos son un 400.
 *
 * <p>{@code sort} tiene la forma {@code campo} o {@code campo,asc|desc}. Cada
 * listado declara los campos que acepta y a que propiedad JPA corresponden.
 * Siempre se agrega {@code id} como desempate: sin un orden total, dos filas
 * con el mismo {@code openedAt} pueden repetirse o saltarse entre paginas.
 */
public final class PageQuery {

    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    private PageQuery() {
    }

    public static Pageable of(Integer page, Integer size, String sort,
            Map<String, String> allowedSorts, Sort defaultSort) {
        int pageNumber = page == null ? 0 : page;
        int pageSize = size == null ? DEFAULT_SIZE : size;
        if (pageNumber < 0) {
            throw new InvalidPageRequestException("page debe ser 0 o mayor.");
        }
        if (pageSize < 1 || pageSize > MAX_SIZE) {
            throw new InvalidPageRequestException("size debe estar entre 1 y " + MAX_SIZE + ".");
        }
        Sort order = sort == null || sort.isBlank() ? defaultSort : parseSort(sort, allowedSorts);
        return PageRequest.of(pageNumber, pageSize, order.and(Sort.by(Sort.Direction.ASC, "id")));
    }

    private static Sort parseSort(String sort, Map<String, String> allowedSorts) {
        String[] parts = sort.split(",", -1);
        String property = allowedSorts.get(parts[0].trim());
        if (property == null || parts.length > 2) {
            throw new InvalidPageRequestException("sort solo admite " + String.join(", ", allowedSorts.keySet())
                    + ", con ,asc o ,desc opcional.");
        }
        if (parts.length == 1) {
            return Sort.by(Sort.Direction.ASC, property);
        }
        return switch (parts[1].trim().toLowerCase(java.util.Locale.ROOT)) {
            case "asc" -> Sort.by(Sort.Direction.ASC, property);
            case "desc" -> Sort.by(Sort.Direction.DESC, property);
            default -> throw new InvalidPageRequestException("La direccion de sort debe ser asc o desc.");
        };
    }
}
