package com.callejon9.order.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record AddOrderItemsRequest(
        @NotEmpty @Size(max = 50) @Valid List<AddOrderItemRequest> items) {
}
