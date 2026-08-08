package com.callejon9.inventory.service;

import com.callejon9.inventory.domain.InventoryItem;
import com.callejon9.inventory.repository.InventoryItemRepository;
import com.callejon9.shared.error.BusinessRuleException;
import com.callejon9.shared.error.ResourceNotFoundException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Catalogo de insumos. El stock NUNCA se toca desde aqui: ver InventoryMovementService. */
@Service
public class InventoryItemService {

    private final InventoryItemRepository itemRepository;

    public InventoryItemService(InventoryItemRepository itemRepository) {
        this.itemRepository = itemRepository;
    }

    /**
     * Por defecto solo insumos activos. La pantalla de inventario pasa
     * {@code includeInactive = true} para poder ver (y reactivar) los dados de
     * baja, que de otro modo quedarian atrapados sin forma de deshacerse.
     */
    @Transactional(readOnly = true)
    public List<InventoryItem> listItems(boolean includeInactive) {
        return includeInactive
                ? itemRepository.findAllByOrderByName()
                : itemRepository.findByActiveTrueOrderByName();
    }

    @Transactional
    public InventoryItem createItem(String name, String unit,
                                    BigDecimal minStock, BigDecimal unitCost) {
        if (itemRepository.existsByName(name)) {
            throw new BusinessRuleException("Ya existe un insumo llamado '" + name + "'.");
        }

        return itemRepository.save(InventoryItem.builder()
                .name(name)
                .unit(unit)
                .stock(BigDecimal.ZERO)
                .minStock(Objects.requireNonNullElse(minStock, BigDecimal.ZERO))
                .unitCost(Objects.requireNonNullElse(unitCost, BigDecimal.ZERO))
                .active(true)
                .build());
    }

    /**
     * Corrige nombre, unidad, minimo y costo. No toca el stock: cambiarlo aqui
     * dejaria un salto en el historial que ninguna fila explicaria.
     */
    @Transactional
    public InventoryItem updateItem(UUID itemId, String name, String unit,
                                    BigDecimal minStock, BigDecimal unitCost) {
        InventoryItem item = itemRepository.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("El insumo no existe."));

        if (itemRepository.existsByNameAndIdNot(name, itemId)) {
            throw new BusinessRuleException("Ya existe un insumo llamado '" + name + "'.");
        }

        item.setName(name);
        item.setUnit(unit);
        item.setMinStock(Objects.requireNonNullElse(minStock, BigDecimal.ZERO));
        item.setUnitCost(Objects.requireNonNullElse(unitCost, BigDecimal.ZERO));
        return itemRepository.save(item);
    }

    /**
     * Da de alta o de baja un insumo. La baja es siempre logica: el insumo
     * queda referenciado por los movimientos que lo tocaron, y borrarlo
     * perderia ese historico.
     */
    @Transactional
    public InventoryItem setActive(UUID itemId, boolean active) {
        InventoryItem item = itemRepository.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("El insumo no existe."));

        item.setActive(active);
        return itemRepository.save(item);
    }
}
