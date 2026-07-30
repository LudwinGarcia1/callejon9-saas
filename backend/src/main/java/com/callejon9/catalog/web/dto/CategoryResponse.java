package com.callejon9.catalog.web.dto;

import com.callejon9.catalog.domain.Category;
import java.util.UUID;

public record CategoryResponse(UUID id, String name, int sortOrder) {

    public static CategoryResponse from(Category category) {
        return new CategoryResponse(category.getId(), category.getName(), category.getSortOrder());
    }
}
