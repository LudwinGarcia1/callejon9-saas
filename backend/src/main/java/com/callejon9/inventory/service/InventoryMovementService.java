package com.callejon9.inventory.service;

import com.callejon9.inventory.domain.InventoryItem;
import com.callejon9.inventory.domain.InventoryMovement;
import com.callejon9.inventory.domain.InventoryMovementType;
import com.callejon9.inventory.repository.InventoryItemRepository;
import com.callejon9.inventory.repository.InventoryMovementRepository;
import com.callejon9.inventory.web.dto.InventoryMovementRow;
import com.callejon9.shared.error.BusinessRuleException;
import com.callejon9.shared.error.ResourceNotFoundException;
import com.callejon9.shared.time.BusinessCalendar;
import com.callejon9.shared.time.InstantRange;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * El ledger. Todo cambio de stock pasa por aqui: no existe ningun otro camino
 * que toque inventory_items.stock, y por eso la suma de los movimientos de un
 * insumo cuadra siempre con su columna.
 */
@Service
public class InventoryMovementService {

    private final InventoryItemRepository itemRepository;
    private final InventoryMovementRepository movementRepository;
    private final BusinessCalendar businessCalendar;

    public InventoryMovementService(InventoryItemRepository itemRepository,
                                    InventoryMovementRepository movementRepository,
                                    BusinessCalendar businessCalendar) {
        this.itemRepository = itemRepository;
        this.movementRepository = movementRepository;
        this.businessCalendar = businessCalendar;
    }

    /**
     * Registra un movimiento y aplica su efecto sobre el stock, en una sola
     * transaccion.
     *
     * En un ADJUSTMENT el delta se calcula aqui, contra el stock que acaba de
     * leerse: {@code countedStock - stock}. Es la unica forma de que el
     * numero guardado corresponda al conteo que la persona hizo.
     *
     * El insumo se lee con lock pesimista, y el lock va ANTES de leer el
     * stock: tanto el delta de un ajuste como la suma de cualquier otro
     * movimiento se calculan a partir de ese valor, y leerlo sin bloquear es
     * justo la ventana por la que se pierde un movimiento.
     */
    @Transactional
    public InventoryMovement register(UUID itemId, InventoryMovementType type,
                                      BigDecimal quantity, BigDecimal countedStock,
                                      String reason, UUID userId) {
        InventoryItem item = itemRepository.findByIdForUpdate(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("El insumo no existe."));

        if (!item.isActive()) {
            throw new BusinessRuleException("El insumo '" + item.getName()
                    + "' esta dado de baja. Reactivalo para poder moverlo.");
        }

        BigDecimal effectiveQuantity = type == InventoryMovementType.ADJUSTMENT
                ? countedStock.subtract(item.getStock())
                : quantity;

        if (type == InventoryMovementType.ADJUSTMENT && effectiveQuantity.signum() == 0) {
            throw new BusinessRuleException(
                    "El conteo coincide con el stock registrado: no hay nada que ajustar.");
        }

        item.apply(type, effectiveQuantity);
        itemRepository.save(item);

        return movementRepository.save(InventoryMovement.builder()
                .inventoryItemId(item.getId())
                .movementType(type)
                .quantity(effectiveQuantity)
                .reason(type == InventoryMovementType.ADJUSTMENT
                        ? adjustmentReason(countedStock, reason)
                        : reason)
                .userId(userId)
                .build());
    }

    /**
     * El motivo de un ajuste conserva el numero que se conto, que el delta por
     * si solo pierde: guardar solo "-3" no dice si se contaron 8 sobre 11 o 5
     * sobre 8.
     */
    private String adjustmentReason(BigDecimal countedStock, String reason) {
        String prefix = "Conteo fisico: " + countedStock.stripTrailingZeros().toPlainString();
        return reason == null || reason.isBlank() ? prefix : prefix + " - " + reason.trim();
    }

    /**
     * El rango se resuelve en la zona horaria del negocio, no en UTC (ver
     * {@link BusinessCalendar}): un movimiento registrado a las 19:00 en
     * Mexico City cae en el dia siguiente en UTC, y el listado se vaciaria a
     * media cena. Sin parametros, hoy; con uno solo, ese mismo dia.
     */
    @Transactional(readOnly = true)
    public List<InventoryMovementRow> listMovements(LocalDate from, LocalDate to, UUID itemId) {
        LocalDate today = businessCalendar.today();
        LocalDate effectiveFrom = from != null ? from : (to != null ? to : today);
        LocalDate effectiveTo = to != null ? to : effectiveFrom;

        InstantRange range = businessCalendar.toInstantRange(effectiveFrom, effectiveTo);
        return movementRepository.findHistory(range.start(), range.endExclusive(), itemId);
    }
}
