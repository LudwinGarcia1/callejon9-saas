-- ============================================================
-- Planes de las consultas paginadas de CAL-16 sobre el dataset de
-- scripts/evidencia-paginacion-seed.sql.
--
-- Se ejecuta COMO callejon9_app, para que RLS agregue el mismo
-- predicado de tenant que en la aplicacion. El SQL es equivalente al que
-- genera Hibernate para cada pagina: mismo WHERE, ORDER BY (con id como
-- desempate) y LIMIT. Termina en ROLLBACK; no modifica datos.
--
-- Uso:
--   docker cp scripts/evidencia-paginacion-explain.sql callejon9-db:/tmp/
--   docker exec callejon9-db psql -U callejon9_app -d callejon9 -f /tmp/evidencia-paginacion-explain.sql
-- ============================================================

\pset pager off
BEGIN;
SELECT set_config('app.tenant_id', (SELECT id::text FROM tenants WHERE slug = 'escala-a'), true);
\echo '=== 1. GET /orders?open=true&size=100 (pagina)'
EXPLAIN (ANALYZE, BUFFERS, COSTS OFF, TIMING OFF)
SELECT o.id, o.folio, o.table_id, o.waiter_id, o.guest_count, o.status, o.total, o.opened_at
FROM orders o WHERE o.status IN ('NEW','SENT','READY')
ORDER BY o.opened_at DESC, o.id ASC LIMIT 100;
\echo '=== 1b. conteo de la pagina anterior'
EXPLAIN (ANALYZE, BUFFERS, COSTS OFF, TIMING OFF)
SELECT count(o.id) FROM orders o WHERE o.status IN ('NEW','SENT','READY');
\echo '=== 2. GET /orders?size=20 (todas, mas reciente primero)'
EXPLAIN (ANALYZE, BUFFERS, COSTS OFF, TIMING OFF)
SELECT o.id, o.folio, o.status, o.total, o.opened_at FROM orders o
ORDER BY o.opened_at DESC, o.id ASC LIMIT 20;
\echo '=== 2b. conteo de todas'
EXPLAIN (ANALYZE, BUFFERS, COSTS OFF, TIMING OFF)
SELECT count(o.id) FROM orders o;
\echo '=== 3. GET /sales?from&to (30 dias) size=25'
EXPLAIN (ANALYZE, BUFFERS, COSTS OFF, TIMING OFF)
SELECT s.id, o.folio, t.number, u.full_name, tk.id, s.payment_method, s.subtotal, s.tip, s.total, s.created_at
FROM sales s
JOIN orders o ON o.id = s.order_id
LEFT JOIN restaurant_tables t ON t.id = s.table_id
LEFT JOIN users u ON u.id = s.cashier_id
LEFT JOIN tickets tk ON tk.sale_id = s.id
WHERE s.status = 'COMPLETED' AND s.created_at >= now() - interval '30 days' AND s.created_at < now()
ORDER BY s.created_at DESC, s.id ASC LIMIT 25;
\echo '=== 3b. conteo de ventas del rango'
EXPLAIN (ANALYZE, BUFFERS, COSTS OFF, TIMING OFF)
SELECT count(s.id) FROM sales s
WHERE s.status = 'COMPLETED' AND s.created_at >= now() - interval '30 days' AND s.created_at < now();
\echo '=== totales visibles para escala-a (RLS)'
SELECT (SELECT count(*) FROM orders) AS orders_visibles, (SELECT count(*) FROM sales) AS sales_visibles;
ROLLBACK;
