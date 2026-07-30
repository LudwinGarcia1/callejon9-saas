package com.callejon9.table.web.dto;

import com.callejon9.table.domain.RestaurantTable;
import java.util.UUID;

public record TableResponse(
        UUID id,
        int number,
        int capacity,
        String status,
        UUID waiterId) {

    public static TableResponse from(RestaurantTable table) {
        return new TableResponse(
                table.getId(), table.getNumber(), table.getCapacity(),
                table.getStatus().name(), table.getWaiterId());
    }
}
