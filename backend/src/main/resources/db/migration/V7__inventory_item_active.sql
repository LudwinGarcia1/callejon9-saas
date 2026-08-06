-- ============================================================
-- Baja logica de insumos.
--
-- Un insumo esta referenciado por los movimientos que lo tocaron
-- (inventory_movements.inventory_item_id), asi que borrarlo perderia
-- el historico. Mismo patron que products, restaurant_tables y users:
-- active = false, nunca DELETE.
--
-- No hace falta tocar la politica RLS ni los permisos: la politica
-- tenant_isolation aplica a la tabla completa y los GRANT de V4 se
-- otorgaron a nivel de tabla, no de columna.
-- ============================================================

ALTER TABLE inventory_items
    ADD COLUMN active boolean NOT NULL DEFAULT true;
