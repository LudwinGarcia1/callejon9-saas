package com.callejon9.order.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record AddOrderItemsRequest(
        @NotEmpty @Valid List<AddOrderItemRequest> items) {
}
