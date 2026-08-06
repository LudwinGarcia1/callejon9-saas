package com.callejon9.inventory;

import com.callejon9.inventory.domain.InventoryItem;
import com.callejon9.inventory.domain.InventoryMovementType;
import com.callejon9.inventory.domain.StockLevel;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Aritmetica del stock y nivel derivado: comportamiento inherente de la
 * entidad, sin Spring ni base de datos.
 */
@DisplayName("InventoryItem")
class InventoryItemTest {

    private InventoryItem itemWith(String stock, String minStock) {
        return InventoryItem.builder()
                .name("Cebolla")
                .unit("kg")
                .stock(new BigDecimal(stock))
                .minStock(new BigDecimal(minStock))
                .unitCost(BigDecimal.ZERO)
                .active(true)
                .build();
    }

    @Test
    @DisplayName("una entrada suma al stock")
    void anEntryAddsToStock() {
        InventoryItem item = itemWith("10.000", "0");

        item.apply(InventoryMovementType.IN, new BigDecimal("5.500"));

        assertThat(item.getStock()).isEqualByComparingTo("15.500");
    }

    @Test
    @DisplayName("una salida y una merma restan del stock")
    void anExitAndAWasteSubtractFromStock() {
        InventoryItem item = itemWith("10.000", "0");

        item.apply(InventoryMovementType.OUT, new BigDecimal("2.000"));
        item.apply(InventoryMovementType.WASTE, new BigDecimal("1.500"));

        assertThat(item.getStock()).isEqualByComparingTo("6.500");
    }

    @Test
    @DisplayName("un ajuste suma la diferencia con signo, positiva o negativa")
    void anAdjustmentAddsTheSignedDelta() {
        InventoryItem item = itemWith("11.000", "0");

        item.apply(InventoryMovementType.ADJUSTMENT, new BigDecimal("-3.000"));
        assertThat(item.getStock()).isEqualByComparingTo("8.000");

        item.apply(InventoryMovementType.ADJUSTMENT, new BigDecimal("2.000"));
        assertThat(item.getStock()).isEqualByComparingTo("10.000");
    }

    @Test
    @DisplayName("el stock puede quedar negativo: apply no lanza ni topa en cero")
    void stockIsAllowedToGoNegative() {
        InventoryItem item = itemWith("1.000", "0");

        item.apply(InventoryMovementType.OUT, new BigDecimal("4.000"));

        assertThat(item.getStock()).isEqualByComparingTo("-3.000");
    }

    @Test
    @DisplayName("stock negativo es NEGATIVE incluso con minimo en cero")
    void negativeStockIsNegativeEvenWithoutAConfiguredMinimum() {
        assertThat(itemWith("-0.500", "0").level()).isEqualTo(StockLevel.NEGATIVE);
    }

    @Test
    @DisplayName("stock igual al minimo ya es LOW")
    void stockAtTheMinimumIsAlreadyLow() {
        assertThat(itemWith("5.000", "5.000").level()).isEqualTo(StockLevel.LOW);
    }

    @Test
    @DisplayName("stock bajo el minimo es LOW")
    void stockBelowTheMinimumIsLow() {
        assertThat(itemWith("4.000", "5.000").level()).isEqualTo(StockLevel.LOW);
    }

    @Test
    @DisplayName("minimo en cero no genera alerta: significa 'no configure minimo'")
    void aZeroMinimumNeverRaisesAnAlert() {
        assertThat(itemWith("0.000", "0").level()).isEqualTo(StockLevel.OK);
        assertThat(itemWith("50.000", "0").level()).isEqualTo(StockLevel.OK);
    }

    @Test
    @DisplayName("stock sobre el minimo es OK")
    void stockAboveTheMinimumIsOk() {
        assertThat(itemWith("6.000", "5.000").level()).isEqualTo(StockLevel.OK);
    }
}
