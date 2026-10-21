package com.callejon9.inventory.service;

import com.callejon9.inventory.domain.InventoryItem;
import com.callejon9.inventory.domain.InventoryMovement;
import com.callejon9.inventory.domain.InventoryMovementType;
import com.callejon9.inventory.domain.StockLevel;
import com.callejon9.inventory.repository.InventoryItemRepository;
import com.callejon9.inventory.repository.InventoryMovementRepository;
import com.callejon9.shared.error.BusinessRuleException;
import com.callejon9.shared.error.ResourceNotFoundException;
import com.callejon9.shared.time.BusinessCalendar;
import java.math.BigDecimal;
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
@DisplayName("UT · InventoryMovementService")
class InventoryMovementServiceTest {

    @Mock
    private InventoryItemRepository itemRepository;

    @Mock
    private InventoryMovementRepository movementRepository;

    @Mock
    private BusinessCalendar businessCalendar;

    @InjectMocks
    private InventoryMovementService movementService;

    @Test
    @DisplayName("register rechaza cualquier movimiento sobre un insumo dado de baja")
    void registerMovementOnInactiveItemThrows() {
        UUID itemId = UUID.randomUUID();
        InventoryItem item = InventoryItem.builder()
                .name("Jitomate")
                .unit("kg")
                .stock(new BigDecimal("10.000"))
                .active(false)
                .build();
        item.setId(itemId);

        when(itemRepository.findByIdForUpdate(itemId)).thenReturn(Optional.of(item));

        assertThatThrownBy(() -> movementService.register(itemId, InventoryMovementType.IN,
                new BigDecimal("5.000"), null, "Compra", UUID.randomUUID()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("esta dado de baja. Reactivalo para poder moverlo.");

        verify(movementRepository, never()).save(any());
        verify(itemRepository, never()).save(any());
    }

    @Test
    @DisplayName("register rechaza un ajuste cuando el conteo coincide exactamente con el stock registrado")
    void registerAdjustmentEqualToCurrentStockThrows() {
        UUID itemId = UUID.randomUUID();
        InventoryItem item = InventoryItem.builder()
                .name("Harina")
                .unit("kg")
                .stock(new BigDecimal("15.000"))
                .active(true)
                .build();
        item.setId(itemId);

        when(itemRepository.findByIdForUpdate(itemId)).thenReturn(Optional.of(item));

        assertThatThrownBy(() -> movementService.register(itemId, InventoryMovementType.ADJUSTMENT,
                null, new BigDecimal("15.000"), "Inventario mensual", UUID.randomUUID()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("El conteo coincide con el stock registrado: no hay nada que ajustar.");

        verify(movementRepository, never()).save(any());
        verify(itemRepository, never()).save(any());
    }

    @Test
    @DisplayName("register permite salidas que dejan el stock negativo y actualiza el nivel a NEGATIVE")
    void registerOutMovementLeavingNegativeStockIsAllowedAndLevelBecomesNegative() {
        UUID itemId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        InventoryItem item = InventoryItem.builder()
                .name("Carne")
                .unit("kg")
                .stock(new BigDecimal("2.000"))
                .minStock(new BigDecimal("5.000"))
                .unitCost(new BigDecimal("100.00"))
                .active(true)
                .build();
        item.setId(itemId);

        when(itemRepository.findByIdForUpdate(itemId)).thenReturn(Optional.of(item));
        when(movementRepository.save(any(InventoryMovement.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        InventoryMovement movement = movementService.register(itemId, InventoryMovementType.OUT,
                new BigDecimal("5.000"), null, "Cocina turno tarde", userId);

        assertThat(item.getStock()).isEqualByComparingTo("-3.000");
        assertThat(item.level()).isEqualTo(StockLevel.NEGATIVE);
        assertThat(movement.getQuantity()).isEqualByComparingTo("5.000");
        assertThat(movement.getMovementType()).isEqualTo(InventoryMovementType.OUT);
        verify(itemRepository).save(item);
        verify(movementRepository).save(any(InventoryMovement.class));
    }

    @Test
    @DisplayName("register con entrada IN incrementa el stock correctamente")
    void registerInMovementIncreasesStock() {
        UUID itemId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        InventoryItem item = InventoryItem.builder()
                .name("Queso")
                .unit("kg")
                .stock(new BigDecimal("10.000"))
                .minStock(new BigDecimal("2.000"))
                .unitCost(new BigDecimal("50.00"))
                .active(true)
                .build();
        item.setId(itemId);

        when(itemRepository.findByIdForUpdate(itemId)).thenReturn(Optional.of(item));
        when(movementRepository.save(any(InventoryMovement.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        movementService.register(itemId, InventoryMovementType.IN,
                new BigDecimal("8.000"), null, "Recepcion proveedor", userId);

        assertThat(item.getStock()).isEqualByComparingTo("18.000");
        assertThat(item.level()).isEqualTo(StockLevel.OK);
        verify(itemRepository).save(item);
        verify(movementRepository).save(any(InventoryMovement.class));
    }

    @Test
    @DisplayName("register con ADJUSTMENT calcula el delta y prefija el conteo en el motivo")
    void registerAdjustmentCalculatesDeltaAndPrefixedReason() {
        UUID itemId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        InventoryItem item = InventoryItem.builder()
                .name("Leche")
                .unit("litro")
                .stock(new BigDecimal("10.000"))
                .minStock(new BigDecimal("2.000"))
                .unitCost(new BigDecimal("25.00"))
                .active(true)
                .build();
        item.setId(itemId);

        when(itemRepository.findByIdForUpdate(itemId)).thenReturn(Optional.of(item));
        when(movementRepository.save(any(InventoryMovement.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        InventoryMovement movement = movementService.register(itemId, InventoryMovementType.ADJUSTMENT,
                null, new BigDecimal("7.000"), "Cajas abiertas", userId);

        assertThat(item.getStock()).isEqualByComparingTo("7.000");
        assertThat(movement.getQuantity()).isEqualByComparingTo("-3.000");
        assertThat(movement.getReason()).contains("Conteo fisico: 7 - Cajas abiertas");
        verify(itemRepository).save(item);
    }

    @Test
    @DisplayName("register arroja ResourceNotFoundException si el insumo no existe")
    void registerNonexistentItemThrows() {
        UUID itemId = UUID.randomUUID();
        when(itemRepository.findByIdForUpdate(itemId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> movementService.register(itemId, InventoryMovementType.IN,
                new BigDecimal("5.000"), null, "Prueba", UUID.randomUUID()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("El insumo no existe.");

        verify(movementRepository, never()).save(any());
    }
}
