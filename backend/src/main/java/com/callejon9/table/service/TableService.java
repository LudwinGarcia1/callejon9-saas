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

    /**
     * Lista las mesas. Por defecto solo las activas: es lo que necesitan el
     * mapa de mesas del mesero, cocina y caja. La pantalla de administracion
     * pasa {@code includeInactive = true} para poder ver (y reactivar) las
     * mesas dadas de baja, que de otro modo quedarian atrapadas sin forma de
     * deshacerse.
     */
    @Transactional(readOnly = true)
    public List<RestaurantTable> listTables(boolean includeInactive) {
        return includeInactive
                ? tableRepository.findAllByOrderByNumber()
                : tableRepository.findByActiveTrueOrderByNumber();
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

        if (!table.isActive()) {
            throw new BusinessRuleException(
                    "La mesa " + table.getNumber() + " esta dada de baja.");
        }

        if (table.getStatus() != TableStatus.FREE) {
            throw new BusinessRuleException(
                    "La mesa " + table.getNumber() + " no esta disponible (" + table.getStatus() + ").");
        }

        table.setStatus(TableStatus.OCCUPIED);
        table.setWaiterId(waiterId);
        return tableRepository.save(table);
    }

    /**
     * Cambio manual del estado de servicio: reservar una mesa, mandarla a
     * limpieza o devolverla a libre.
     *
     * <p>{@code OCCUPIED} queda fuera en los dos sentidos, y por la misma
     * razon: ese estado solo lo justifica una comanda abierta. Ponerlo a mano
     * crearia una mesa ocupada sin comanda, y quitarlo a una mesa que lo tiene
     * dejaria la comanda viva sobre una mesa marcada como libre, lista para
     * que se siente otro grupo. Para eso estan cancelar y cobrar.
     *
     * <p>Toma el mismo lock que {@link #occupy}: sin el, reservar una mesa y
     * abrir una comanda sobre ella pueden leer ambos {@code FREE} y escribir
     * cada uno lo suyo.
     */
    @Transactional
    public RestaurantTable changeStatus(UUID tableId, TableStatus newStatus) {
        if (newStatus == TableStatus.OCCUPIED) {
            throw new BusinessRuleException(
                    "Una mesa se ocupa abriendo una comanda, no cambiando su estado.");
        }

        RestaurantTable table = tableRepository.findByIdForUpdate(tableId)
                .orElseThrow(() -> new ResourceNotFoundException("La mesa no existe."));

        if (!table.isActive()) {
            throw new BusinessRuleException(
                    "La mesa " + table.getNumber() + " esta dada de baja.");
        }

        if (table.getStatus() == TableStatus.OCCUPIED) {
            throw new BusinessRuleException(
                    "La mesa " + table.getNumber() + " tiene una comanda abierta. "
                            + "Cobrala o cancelala para liberarla.");
        }

        table.setStatus(newStatus);
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

    /** Corrige el numero o la capacidad de una mesa ya creada. */
    @Transactional
    public RestaurantTable updateTable(UUID tableId, int number, int capacity) {
        RestaurantTable table = tableRepository.findById(tableId)
                .orElseThrow(() -> new ResourceNotFoundException("La mesa no existe."));

        if (tableRepository.existsByNumberAndIdNot(number, tableId)) {
            throw new BusinessRuleException(
                    "Ya existe una mesa con el numero " + number + ".");
        }

        table.setNumber(number);
        table.setCapacity(capacity);
        return tableRepository.save(table);
    }

    /**
     * Da de alta o de baja una mesa. La baja es siempre logica (active =
     * false): la mesa queda referenciada por las ordenes historicas que se
     * abrieron en ella, y borrarla perderia esa atribucion. Una mesa dada de
     * baja no aparece en el listado y {@link #occupy} la rechaza.
     */
    @Transactional
    public RestaurantTable setActive(UUID tableId, boolean active) {
        RestaurantTable table = tableRepository.findById(tableId)
                .orElseThrow(() -> new ResourceNotFoundException("La mesa no existe."));

        table.setActive(active);
        return tableRepository.save(table);
    }
}
