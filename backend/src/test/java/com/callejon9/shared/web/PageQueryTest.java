package com.callejon9.shared.web;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("UT · PageQuery")
class PageQueryTest {

    private static final Map<String, String> SORTABLE = Map.of("openedAt", "openedAt", "folio", "folio");
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "openedAt");
    private static final Sort BY_ID = Sort.by(Sort.Direction.ASC, "id");

    @Test
    @DisplayName("sin parametros: pagina 0, tamano por defecto y orden por defecto con id como desempate")
    void defaults() {
        Pageable pageable = PageQuery.of(null, null, null, SORTABLE, NEWEST_FIRST);

        assertThat(pageable.getPageNumber()).isZero();
        assertThat(pageable.getPageSize()).isEqualTo(PageQuery.DEFAULT_SIZE);
        assertThat(pageable.getSort()).isEqualTo(NEWEST_FIRST.and(BY_ID));
    }

    @Test
    @DisplayName("traduce el campo publico a la propiedad JPA y respeta la direccion")
    void parsesAllowedSort() {
        assertThat(PageQuery.of(2, 50, "folio,asc", SORTABLE, NEWEST_FIRST).getSort())
                .isEqualTo(Sort.by(Sort.Direction.ASC, "folio").and(BY_ID));
        assertThat(PageQuery.of(0, PageQuery.MAX_SIZE, "openedAt,DESC", SORTABLE, NEWEST_FIRST).getSort())
                .isEqualTo(NEWEST_FIRST.and(BY_ID));
        assertThat(PageQuery.of(0, 1, "folio", SORTABLE, NEWEST_FIRST).getSort())
                .isEqualTo(Sort.by(Sort.Direction.ASC, "folio").and(BY_ID));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, PageQuery.MAX_SIZE + 1})
    @DisplayName("rechaza un size fuera de 1..MAX_SIZE")
    void rejectsSizeOutOfRange(int size) {
        assertThatThrownBy(() -> PageQuery.of(0, size, null, SORTABLE, NEWEST_FIRST))
                .isInstanceOf(InvalidPageRequestException.class);
    }

    @Test
    @DisplayName("rechaza una pagina negativa")
    void rejectsNegativePage() {
        assertThatThrownBy(() -> PageQuery.of(-1, 10, null, SORTABLE, NEWEST_FIRST))
                .isInstanceOf(InvalidPageRequestException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"waiterId", "tenantId,asc", "folio,up", "folio,asc,extra", ",asc"})
    @DisplayName("rechaza campos fuera de la allowlist y direcciones invalidas")
    void rejectsUnknownSort(String sort) {
        assertThatThrownBy(() -> PageQuery.of(0, 10, sort, SORTABLE, NEWEST_FIRST))
                .isInstanceOf(InvalidPageRequestException.class);
    }
}
