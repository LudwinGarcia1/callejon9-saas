package com.callejon9.inventory.web.dto;

import com.callejon9.inventory.domain.InventoryItem;
import com.callejon9.inventory.domain.StockLevel;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * {@code level} viaja ya calculado por la entidad: la interfaz pinta una
 * insignia sin recalcular umbrales de negocio en el cliente.
 */
public record InventoryItemResponse(
        UUID id,
        String name,
        String unit,
        BigDecimal stock,
        BigDecimal minStock,
        BigDecimal unitCost,
        boolean active,
        StockLevel level) {

    public static InventoryItemResponse from(InventoryItem item) {
        return new InventoryItemResponse(
                item.getId(), item.getName(), item.getUnit(),
                item.getStock(), item.getMinStock(), item.getUnitCost(),
                item.isActive(), item.level());
    }
}
