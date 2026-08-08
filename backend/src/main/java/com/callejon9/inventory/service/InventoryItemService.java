package com.callejon9.inventory.service;

import com.callejon9.inventory.domain.InventoryItem;
import com.callejon9.inventory.domain.InventoryMovementType;
import com.callejon9.inventory.repository.InventoryItemRepository;
import com.callejon9.inventory.repository.InventoryMovementRepository;
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
    private final InventoryMovementRepository movementRepository;
    private final InventoryMovementService movementService;

    public InventoryItemService(InventoryItemRepository itemRepository,
                                InventoryMovementRepository movementRepository,
                                InventoryMovementService movementService) {
        this.itemRepository = itemRepository;
        this.movementRepository = movementRepository;
        this.movementService = movementService;
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

    /**
     * El stock inicial no se escribe en la columna: se registra como un
     * movimiento IN, y el movimiento es el que mueve el stock. Asi no existe
     * ningun camino por el que la columna cambie sin una fila que lo explique,
     * ni siquiera el alta.
     *
     * Es una sola transaccion: si el movimiento falla, el insumo no queda
     * creado con un stock que nada justifica.
     */
    @Transactional
    public InventoryItem createItem(String name, String unit, BigDecimal minStock,
                                    BigDecimal unitCost, BigDecimal initialStock, UUID userId) {
        if (itemRepository.existsByName(name)) {
            throw new BusinessRuleException("Ya existe un insumo llamado '" + name + "'.");
        }

        InventoryItem item = itemRepository.save(InventoryItem.builder()
                .name(name)
                .unit(unit)
                .stock(BigDecimal.ZERO)
                .minStock(Objects.requireNonNullElse(minStock, BigDecimal.ZERO))
                .unitCost(Objects.requireNonNullElse(unitCost, BigDecimal.ZERO))
                .active(true)
                .build());

        if (initialStock != null && initialStock.signum() > 0) {
            movementService.register(item.getId(), InventoryMovementType.IN,
                    initialStock, null, "Stock inicial", userId);
        }
        return item;
    }

    /**
     * Corrige nombre, unidad, minimo y costo. No toca el stock: cambiarlo aqui
     * dejaria un salto en el historial que ninguna fila explicaria.
     *
     * La unidad queda fija en cuanto el insumo tiene movimientos. Sin esta
     * regla, un 20 registrado en kilos se convierte en gramos y nada en el
     * historial dice en que unidad se capturo cada fila. Mandar la misma
     * unidad no cuenta como cambio.
     */
    @Transactional
    public InventoryItem updateItem(UUID itemId, String name, String unit,
                                    BigDecimal minStock, BigDecimal unitCost) {
        InventoryItem item = itemRepository.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("El insumo no existe."));

        if (itemRepository.existsByNameAndIdNot(name, itemId)) {
            throw new BusinessRuleException("Ya existe un insumo llamado '" + name + "'.");
        }

        if (!item.getUnit().equals(unit) && movementRepository.existsByInventoryItemId(itemId)) {
            throw new BusinessRuleException("El insumo '" + item.getName() + "' ya tiene movimientos en '"
                    + item.getUnit() + "'. Cambiar la unidad haria ilegible su historial; "
                    + "crea otro insumo con la unidad correcta.");
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
