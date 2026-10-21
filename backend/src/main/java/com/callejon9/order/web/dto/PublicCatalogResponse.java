package com.callejon9.order.web.dto;

import com.callejon9.catalog.web.dto.CategoryResponse;
import com.callejon9.catalog.web.dto.ProductResponse;
import java.util.List;

public record PublicCatalogResponse(
        String restaurantName,
        String slug,
        List<CategoryResponse> categories,
        List<ProductResponse> products) {
}
