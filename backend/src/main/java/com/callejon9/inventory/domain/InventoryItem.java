package com.callejon9.inventory.domain;

import com.callejon9.shared.domain.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "inventory_items")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryItem extends TenantScopedEntity {

    @Column(nullable = false, length = 160)
    private String name;

    @Column(nullable = false, length = 20)
    private String unit;

    @Column(nullable = false)
    private BigDecimal stock;

    @Column(name = "min_stock", nullable = false)
    private BigDecimal minStock;

    @Column(name = "unit_cost", nullable = false)
    private BigDecimal unitCost;

    @Column(nullable = false)
    private boolean active;

    /**
     * Aplica el efecto de un movimiento sobre el stock. Nunca lanza y nunca
     * topa en cero: negarse a registrar el movimiento no produce la cebolla
     * que el cocinero tiene en la mano, produce que dejen de usar el sistema.
     * Un stock negativo se permite y se senala (ver {@link #level()}).
     */
    public void apply(InventoryMovementType type, BigDecimal quantity) {
        this.stock = this.stock.add(type.signedEffect(quantity));
    }

    /**
     * La alerta de minimo exige {@code minStock > 0}. La columna tiene
     * DEFAULT 0, asi que sin esa condicion todo insumo recien creado con
     * stock 0 y minimo 0 apareceria en alerta, y la lista de alertas -- que
     * es la mitad del valor del modulo -- naceria llena de ruido.
     * {@code minStock = 0} significa "no configure minimo".
     */
    public StockLevel level() {
        if (stock.signum() < 0) {
            return StockLevel.NEGATIVE;
        }
        if (minStock.signum() > 0 && stock.compareTo(minStock) <= 0) {
            return StockLevel.LOW;
        }
        return StockLevel.OK;
    }
}
