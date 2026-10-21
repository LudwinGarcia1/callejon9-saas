package com.callejon9.inventory.service;

import com.callejon9.inventory.domain.InventoryItem;
import com.callejon9.inventory.domain.InventoryMovementType;
import com.callejon9.inventory.repository.InventoryItemRepository;
import com.callejon9.inventory.repository.InventoryMovementRepository;
import com.callejon9.shared.error.BusinessRuleException;
import com.callejon9.shared.error.ResourceNotFoundException;
import java.math.BigDecimal;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("UT · InventoryItemService")
class InventoryItemServiceTest {

    @Mock
    private InventoryItemRepository itemRepository;

    @Mock
    private InventoryMovementRepository movementRepository;

    @Mock
    private InventoryMovementService movementService;

    @InjectMocks
    private InventoryItemService itemService;

    @Test
    @DisplayName("createItem con stock inicial guarda stock en cero y delega el alta como movimiento IN")
    void createItemWithInitialStockRegistersInMovementAndNeverWritesDirectly() {
        UUID userId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        BigDecimal initialStock = new BigDecimal("25.000");

        when(itemRepository.existsByName("Cebolla")).thenReturn(false);
        when(itemRepository.save(any(InventoryItem.class))).thenAnswer(invocation -> {
            InventoryItem saved = invocation.getArgument(0);
            saved.setId(itemId);
            // El stock inicial debe nacer en cero en la entidad antes del movimiento
            assertThat(saved.getStock()).isEqualByComparingTo(BigDecimal.ZERO);
            return saved;
        });

        InventoryItem item = itemService.createItem("Cebolla", "kg", new BigDecimal("5.000"),
                new BigDecimal("15.50"), initialStock, userId);

        assertThat(item.getName()).isEqualTo("Cebolla");
        assertThat(item.getUnit()).isEqualTo("kg");
        verify(itemRepository).save(any(InventoryItem.class));
        verify(movementService).register(eq(itemId), eq(InventoryMovementType.IN),
                eq(initialStock), isNull(), eq("Stock inicial"), eq(userId));
    }

    @Test
    @DisplayName("createItem sin stock inicial no invoca a movementService")
    void createItemWithoutInitialStockDoesNotRegisterMovement() {
        UUID userId = UUID.randomUUID();
        when(itemRepository.existsByName("Ajo")).thenReturn(false);
        when(itemRepository.save(any(InventoryItem.class))).thenAnswer(invocation -> invocation.getArgument(0));

        InventoryItem item = itemService.createItem("Ajo", "kg", BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, userId);

        assertThat(item.getStock()).isEqualByComparingTo(BigDecimal.ZERO);
        verify(movementService, never()).register(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("createItem rechaza nombre duplicado con BusinessRuleException")
    void createItemDuplicateNameThrows() {
        when(itemRepository.existsByName("Cebolla")).thenReturn(true);

        assertThatThrownBy(() -> itemService.createItem("Cebolla", "kg", null, null, null, UUID.randomUUID()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Ya existe un insumo llamado 'Cebolla'.");

        verify(itemRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateItem rechaza cambiar de unidad si el insumo ya tiene movimientos registrados")
    void updateItemRejectsUnitChangeWhenMovementsExist() {
        UUID itemId = UUID.randomUUID();
        InventoryItem existing = InventoryItem.builder().name("Frijol").unit("kg").stock(new BigDecimal("10.000")).build();
        existing.setId(itemId);

        when(itemRepository.findById(itemId)).thenReturn(Optional.of(existing));
        when(itemRepository.existsByNameAndIdNot("Frijol", itemId)).thenReturn(false);
        when(movementRepository.existsByInventoryItemId(itemId)).thenReturn(true);

        assertThatThrownBy(() -> itemService.updateItem(itemId, "Frijol", "gramos", BigDecimal.ZERO, BigDecimal.ZERO))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("ya tiene movimientos en 'kg'. Cambiar la unidad haria ilegible su historial");

        verify(itemRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateItem permite actualizar manteniendo la misma unidad aunque tenga movimientos")
    void updateItemAllowsSameUnitWhenMovementsExist() {
        UUID itemId = UUID.randomUUID();
        InventoryItem existing = InventoryItem.builder().name("Frijol Negro").unit("kg").stock(new BigDecimal("10.000")).build();
        existing.setId(itemId);

        when(itemRepository.findById(itemId)).thenReturn(Optional.of(existing));
        when(itemRepository.existsByNameAndIdNot("Frijol Pinto", itemId)).thenReturn(false);
        when(itemRepository.save(any(InventoryItem.class))).thenAnswer(invocation -> invocation.getArgument(0));

        InventoryItem updated = itemService.updateItem(itemId, "Frijol Pinto", "kg", new BigDecimal("2.000"), new BigDecimal("35.00"));

        assertThat(updated.getName()).isEqualTo("Frijol Pinto");
        assertThat(updated.getUnit()).isEqualTo("kg");
        verify(itemRepository).save(existing);
    }

    @Test
    @DisplayName("listItems por defecto excluye insumos inactivos")
    void listItemsDefaultExcludesInactive() {
        InventoryItem item = InventoryItem.builder().name("Arroz").active(true).build();
        when(itemRepository.findByActiveTrueOrderByName()).thenReturn(List.of(item));

        List<InventoryItem> result = itemService.listItems(false);

        assertThat(result).containsExactly(item);
        verify(itemRepository).findByActiveTrueOrderByName();
    }

    @Test
    @DisplayName("listItems con includeInactive=true devuelve todos los insumos")
    void listItemsIncludeInactiveReturnsAll() {
        InventoryItem item1 = InventoryItem.builder().name("Arroz").active(true).build();
        InventoryItem item2 = InventoryItem.builder().name("Aceite viejo").active(false).build();
        when(itemRepository.findAllByOrderByName()).thenReturn(List.of(item1, item2));

        List<InventoryItem> result = itemService.listItems(true);

        assertThat(result).containsExactly(item1, item2);
        verify(itemRepository).findAllByOrderByName();
    }
}
