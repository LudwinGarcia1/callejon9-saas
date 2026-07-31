package com.callejon9.table.service;

import com.callejon9.shared.error.BusinessRuleException;
import com.callejon9.shared.error.ResourceNotFoundException;
import com.callejon9.table.domain.RestaurantTable;
import com.callejon9.table.domain.TableStatus;
import com.callejon9.table.repository.RestaurantTableRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * El TenantContext ya esta fijado por TenantFilter antes de que la peticion
 * llegue aqui, asi que @Transactional declarativo es suficiente: no hace
 * falta TransactionTemplate como en TenantOnboardingService (ese caso cruza
 * la frontera control plane / data plane, este no).
 */
@Service
public class TableService {

    private final RestaurantTableRepository tableRepository;

    public TableService(RestaurantTableRepository tableRepository) {
        this.tableRepository = tableRepository;
    }

    @Transactional(readOnly = true)
    public List<RestaurantTable> listActiveTables() {
        return tableRepository.findByActiveTrueOrderByNumber();
    }

    @Transactional
    public RestaurantTable createTable(int number, int capacity) {
        if (tableRepository.existsByNumber(number)) {
            throw new BusinessRuleException(
                    "Ya existe una mesa con el numero " + number + ".");
        }

        return tableRepository.save(RestaurantTable.builder()
                .number(number)
                .capacity(capacity)
                .status(TableStatus.FREE)
                .active(true)
                .build());
    }

    /**
     * Ocupa una mesa libre para abrir una orden nueva.
     *
     * <p>Usa {@code findByIdForUpdate} (SELECT ... FOR UPDATE) en vez de
     * {@code findById}: sin el lock, bajo READ COMMITTED, dos peticiones
     * concurrentes pueden leer ambas el estado FREE, escribir OCCUPIED las
     * dos y crear dos ordenes para la misma mesa sin que ninguna falle. No
     * hay restriccion UNIQUE en {@code orders.table_id} que lo atrape: el
     * lock de fila es lo que serializa a la segunda peticion detras de la
     * primera para que su lectura del estado sea la actualizada.
     */
    @Transactional
    public RestaurantTable occupy(UUID tableId, UUID waiterId) {
        RestaurantTable table = tableRepository.findByIdForUpdate(tableId)
                .orElseThrow(() -> new ResourceNotFoundException("La mesa no existe."));

        if (table.getStatus() != TableStatus.FREE) {
            throw new BusinessRuleException(
                    "La mesa " + table.getNumber() + " no esta disponible (" + table.getStatus() + ").");
        }

        table.setStatus(TableStatus.OCCUPIED);
        table.setWaiterId(waiterId);
        return tableRepository.save(table);
    }

    /** Libera una mesa al cerrar la cuenta (checkout) o al cancelar una orden. */
    @Transactional
    public RestaurantTable free(UUID tableId) {
        RestaurantTable table = tableRepository.findById(tableId)
                .orElseThrow(() -> new ResourceNotFoundException("La mesa no existe."));

        table.setStatus(TableStatus.FREE);
        table.setWaiterId(null);
        return tableRepository.save(table);
    }
}
