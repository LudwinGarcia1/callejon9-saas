package com.callejon9.catalog.web.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateCategoryRequest(
        @NotBlank @Size(max = 120) String name,
        @Min(0) Integer sortOrder) {

    public int sortOrderOrDefault() {
        return sortOrder == null ? 0 : sortOrder;
    }
}
