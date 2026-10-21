package com.callejon9.table.service;

import com.callejon9.shared.error.BusinessRuleException;
import com.callejon9.shared.error.ResourceNotFoundException;
import com.callejon9.table.domain.RestaurantTable;
import com.callejon9.table.domain.TableStatus;
import com.callejon9.table.repository.RestaurantTableRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("UT · TableService")
class TableServiceTest {

    @Mock
    private RestaurantTableRepository tableRepository;

    @InjectMocks
    private TableService tableService;

    @Test
    @DisplayName("createTable guarda la mesa cuando el numero no esta duplicado")
    void createTableSucceeds() {
        when(tableRepository.existsByNumber(5)).thenReturn(false);
        when(tableRepository.save(any(RestaurantTable.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        RestaurantTable table = tableService.createTable(5, 4);

        assertThat(table.getNumber()).isEqualTo(5);
        assertThat(table.getCapacity()).isEqualTo(4);
        assertThat(table.getStatus()).isEqualTo(TableStatus.FREE);
        assertThat(table.isActive()).isTrue();
        verify(tableRepository).save(any(RestaurantTable.class));
    }

    @Test
    @DisplayName("createTable rechaza numero duplicado con BusinessRuleException")
    void createTableDuplicateNumberThrows() {
        when(tableRepository.existsByNumber(5)).thenReturn(true);

        assertThatThrownBy(() -> tableService.createTable(5, 4))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Ya existe una mesa con el numero 5.");

        verify(tableRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateTable actualiza numero y capacidad cuando no colisiona")
    void updateTableSucceeds() {
        UUID id = UUID.randomUUID();
        RestaurantTable existing = RestaurantTable.builder().number(1).capacity(2).status(TableStatus.FREE).active(true).build();
        existing.setId(id);

        when(tableRepository.findById(id)).thenReturn(Optional.of(existing));
        when(tableRepository.existsByNumberAndIdNot(2, id)).thenReturn(false);
        when(tableRepository.save(any(RestaurantTable.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RestaurantTable updated = tableService.updateTable(id, 2, 6);

        assertThat(updated.getNumber()).isEqualTo(2);
        assertThat(updated.getCapacity()).isEqualTo(6);
        verify(tableRepository).save(existing);
    }

    @Test
    @DisplayName("updateTable rechaza numero duplicado con BusinessRuleException")
    void updateTableDuplicateNumberThrows() {
        UUID id = UUID.randomUUID();
        RestaurantTable existing = RestaurantTable.builder().number(1).capacity(2).status(TableStatus.FREE).active(true).build();
        existing.setId(id);

        when(tableRepository.findById(id)).thenReturn(Optional.of(existing));
        when(tableRepository.existsByNumberAndIdNot(3, id)).thenReturn(true);

        assertThatThrownBy(() -> tableService.updateTable(id, 3, 4))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Ya existe una mesa con el numero 3.");

        verify(tableRepository, never()).save(any());
    }

    @Test
    @DisplayName("occupy ocupa una mesa en estado FREE y le asigna el mesero")
    void occupyFreeTableSucceeds() {
        UUID tableId = UUID.randomUUID();
        UUID waiterId = UUID.randomUUID();
        RestaurantTable table = RestaurantTable.builder().number(1).status(TableStatus.FREE).active(true).build();
        table.setId(tableId);

        when(tableRepository.findByIdForUpdate(tableId)).thenReturn(Optional.of(table));
        when(tableRepository.save(any(RestaurantTable.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RestaurantTable occupied = tableService.occupy(tableId, waiterId);

        assertThat(occupied.getStatus()).isEqualTo(TableStatus.OCCUPIED);
        assertThat(occupied.getWaiterId()).isEqualTo(waiterId);
        verify(tableRepository).save(table);
    }

    @Test
    @DisplayName("occupy rechaza ocupar una mesa que ya esta OCCUPIED")
    void occupyAlreadyOccupiedTableThrows() {
        UUID tableId = UUID.randomUUID();
        RestaurantTable table = RestaurantTable.builder().number(4).status(TableStatus.OCCUPIED).active(true).build();
        table.setId(tableId);

        when(tableRepository.findByIdForUpdate(tableId)).thenReturn(Optional.of(table));

        assertThatThrownBy(() -> tableService.occupy(tableId, UUID.randomUUID()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("no esta disponible (OCCUPIED)");

        verify(tableRepository, never()).save(any());
    }

    @Test
    @DisplayName("occupy rechaza ocupar una mesa que esta RESERVED")
    void occupyReservedTableThrows() {
        UUID tableId = UUID.randomUUID();
        RestaurantTable table = RestaurantTable.builder().number(3).status(TableStatus.RESERVED).active(true).build();
        table.setId(tableId);

        when(tableRepository.findByIdForUpdate(tableId)).thenReturn(Optional.of(table));

        assertThatThrownBy(() -> tableService.occupy(tableId, UUID.randomUUID()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("no esta disponible (RESERVED)");

        verify(tableRepository, never()).save(any());
    }

    @Test
    @DisplayName("occupy rechaza ocupar una mesa que esta en CLEANING")
    void occupyCleaningTableThrows() {
        UUID tableId = UUID.randomUUID();
        RestaurantTable table = RestaurantTable.builder().number(2).status(TableStatus.CLEANING).active(true).build();
        table.setId(tableId);

        when(tableRepository.findByIdForUpdate(tableId)).thenReturn(Optional.of(table));

        assertThatThrownBy(() -> tableService.occupy(tableId, UUID.randomUUID()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("no esta disponible (CLEANING)");

        verify(tableRepository, never()).save(any());
    }

    @Test
    @DisplayName("occupy rechaza ocupar una mesa inactiva / dada de baja")
    void occupyInactiveTableThrows() {
        UUID tableId = UUID.randomUUID();
        RestaurantTable table = RestaurantTable.builder().number(7).status(TableStatus.FREE).active(false).build();
        table.setId(tableId);

        when(tableRepository.findByIdForUpdate(tableId)).thenReturn(Optional.of(table));

        assertThatThrownBy(() -> tableService.occupy(tableId, UUID.randomUUID()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("esta dada de baja");

        verify(tableRepository, never()).save(any());
    }

    @Test
    @DisplayName("free libera la mesa y remueve el mesero asignado")
    void freeTableSucceeds() {
        UUID tableId = UUID.randomUUID();
        RestaurantTable table = RestaurantTable.builder().number(10).status(TableStatus.OCCUPIED).waiterId(UUID.randomUUID()).active(true).build();
        table.setId(tableId);

        when(tableRepository.findById(tableId)).thenReturn(Optional.of(table));
        when(tableRepository.save(any(RestaurantTable.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RestaurantTable freed = tableService.free(tableId);

        assertThat(freed.getStatus()).isEqualTo(TableStatus.FREE);
        assertThat(freed.getWaiterId()).isNull();
        verify(tableRepository).save(table);
    }

    @Test
    @DisplayName("changeStatus cambia estado a RESERVED o CLEANING")
    void changeStatusSucceeds() {
        UUID tableId = UUID.randomUUID();
        RestaurantTable table = RestaurantTable.builder().number(8).status(TableStatus.FREE).active(true).build();
        table.setId(tableId);

        when(tableRepository.findByIdForUpdate(tableId)).thenReturn(Optional.of(table));
        when(tableRepository.save(any(RestaurantTable.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RestaurantTable changed = tableService.changeStatus(tableId, TableStatus.RESERVED);

        assertThat(changed.getStatus()).isEqualTo(TableStatus.RESERVED);
        verify(tableRepository).save(table);
    }

    @Test
    @DisplayName("changeStatus rechaza cambio manual a OCCUPIED")
    void changeStatusToOccupiedThrows() {
        UUID tableId = UUID.randomUUID();

        assertThatThrownBy(() -> tableService.changeStatus(tableId, TableStatus.OCCUPIED))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Una mesa se ocupa abriendo una comanda");

        verify(tableRepository, never()).save(any());
    }

    @Test
    @DisplayName("changeStatus rechaza cambiar estado de mesa con orden abierta (OCCUPIED)")
    void changeStatusWhenAlreadyOccupiedThrows() {
        UUID tableId = UUID.randomUUID();
        RestaurantTable table = RestaurantTable.builder().number(11).status(TableStatus.OCCUPIED).active(true).build();
        table.setId(tableId);

        when(tableRepository.findByIdForUpdate(tableId)).thenReturn(Optional.of(table));

        assertThatThrownBy(() -> tableService.changeStatus(tableId, TableStatus.CLEANING))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("tiene una comanda abierta");

        verify(tableRepository, never()).save(any());
    }

    @Test
    @DisplayName("setActive permite dar de baja una mesa libre")
    void setActiveDeactivatesFreeTable() {
        UUID tableId = UUID.randomUUID();
        RestaurantTable table = RestaurantTable.builder().number(12).status(TableStatus.FREE).active(true).build();
        table.setId(tableId);

        when(tableRepository.findById(tableId)).thenReturn(Optional.of(table));
        when(tableRepository.save(any(RestaurantTable.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RestaurantTable deactivated = tableService.setActive(tableId, false);

        assertThat(deactivated.isActive()).isFalse();
        verify(tableRepository).save(table);
    }

    @Test
    @DisplayName("setActive rechaza dar de baja una mesa ocupada con comanda abierta")
    void setActiveRejectsDeactivatingOccupiedTable() {
        UUID tableId = UUID.randomUUID();
        RestaurantTable table = RestaurantTable.builder().number(14).status(TableStatus.OCCUPIED).active(true).build();
        table.setId(tableId);

        when(tableRepository.findById(tableId)).thenReturn(Optional.of(table));

        assertThatThrownBy(() -> tableService.setActive(tableId, false))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("No se puede dar de baja una mesa con comanda abierta.");

        verify(tableRepository, never()).save(any());
    }

    @Test
    @DisplayName("listTables por defecto devuelve solo mesas activas ordenadas por numero")
    void listTablesDefaultExcludesInactive() {
        RestaurantTable t1 = RestaurantTable.builder().number(1).active(true).build();
        when(tableRepository.findByActiveTrueOrderByNumber()).thenReturn(List.of(t1));

        List<RestaurantTable> result = tableService.listTables(false);

        assertThat(result).containsExactly(t1);
        verify(tableRepository).findByActiveTrueOrderByNumber();
    }

    @Test
    @DisplayName("listTables con includeInactive=true devuelve todas las mesas")
    void listTablesIncludeInactiveReturnsAll() {
        RestaurantTable t1 = RestaurantTable.builder().number(1).active(true).build();
        RestaurantTable t2 = RestaurantTable.builder().number(2).active(false).build();
        when(tableRepository.findAllByOrderByNumber()).thenReturn(List.of(t1, t2));

        List<RestaurantTable> result = tableService.listTables(true);

        assertThat(result).containsExactly(t1, t2);
        verify(tableRepository).findAllByOrderByNumber();
    }
}
