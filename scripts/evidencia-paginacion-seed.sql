-- ============================================================
-- Dataset de escala para CAL-16: 3 restaurantes con 10 000 ordenes
-- cada uno (30 abiertas, el resto pagadas) y una venta COMPLETED por
-- orden pagada.
--
-- Solo para una base local desechable: crea tenants escala-a/b/c y no
-- los borra. Se ejecuta como superusuario porque siembra varios
-- restaurantes en una sola transaccion; los planes se miden despues
-- como callejon9_app con scripts/evidencia-paginacion-explain.sql.
--
-- Uso (stack de docker-compose.yml):
--   docker cp scripts/evidencia-paginacion-seed.sql callejon9-db:/tmp/
--   docker exec callejon9-db psql -U postgres -d callejon9 -f /tmp/evidencia-paginacion-seed.sql
-- ============================================================

\set ON_ERROR_STOP on
BEGIN;
INSERT INTO tenants (name, slug) VALUES ('Escala A','escala-a'),('Escala B','escala-b'),('Escala C','escala-c');
INSERT INTO users (tenant_id, email, password_hash, full_name, role)
SELECT id, 'cajero@' || slug || '.test', 'x', 'Cajero', 'CASHIER' FROM tenants WHERE slug LIKE 'escala-%';
-- 30 abiertas por restaurante (NEW/SENT/READY), el resto pagadas.
INSERT INTO orders (tenant_id, folio, status, total, opened_at)
SELECT t.id, 'ESC-' || g,
       CASE WHEN g <= 30 THEN (ARRAY['NEW','SENT','READY'])[1 + g % 3] ELSE 'PAID' END,
       (g % 500) + 0.50,
       now() - make_interval(mins => g * 17)
FROM tenants t, generate_series(1, 10000) g WHERE t.slug LIKE 'escala-%';
INSERT INTO sales (tenant_id, order_id, cashier_id, status, payment_method, subtotal, tip, total, created_at)
SELECT o.tenant_id, o.id, u.id, 'COMPLETED', 'CASH', o.total, 0, o.total, o.opened_at + interval '40 minutes'
FROM orders o JOIN users u ON u.tenant_id = o.tenant_id AND u.role = 'CASHIER'
WHERE o.folio LIKE 'ESC-%' AND o.status = 'PAID';
COMMIT;
ANALYZE orders; ANALYZE sales;
SELECT t.slug, (SELECT count(*) FROM orders o WHERE o.tenant_id = t.id) AS orders,
       (SELECT count(*) FROM sales s WHERE s.tenant_id = t.id) AS sales
FROM tenants t WHERE t.slug LIKE 'escala-%' ORDER BY 1;
