\set ON_ERROR_STOP on

-- Conteos del aterrizaje contra lo que la extraccion reporto.
SELECT landing.etl_assert((SELECT count(*) FROM landing.usuarios) = 6,  'usuarios debe aterrizar 6');
SELECT landing.etl_assert((SELECT count(*) FROM landing.productos) = 21, 'productos debe aterrizar 21');
SELECT landing.etl_assert((SELECT count(*) FROM landing.mesas) = 8,      'mesas debe aterrizar 8');
SELECT landing.etl_assert((SELECT count(*) FROM landing.comandas) = 6,   'comandas debe aterrizar 6');
SELECT landing.etl_assert((SELECT count(*) FROM landing.insumos) = 8,    'insumos debe aterrizar 8');
SELECT landing.etl_assert((SELECT count(*) FROM landing.movimientos_inventario) = 2,
                          'movimientos_inventario debe aterrizar 2');

-- Las funciones de Extended JSON, con las tres formas que el volcado mezcla.
SELECT landing.etl_assert(landing.mongo_num('{"$numberInt":"95"}'::jsonb) = 95,      'mongo_num $numberInt');
SELECT landing.etl_assert(landing.mongo_num('{"$numberDouble":"800.0"}'::jsonb) = 800, 'mongo_num $numberDouble');
SELECT landing.etl_assert(landing.mongo_num('7'::jsonb) = 7,                          'mongo_num escalar');
SELECT landing.etl_assert(landing.mongo_oid('{"$oid":"69771fe45faa0869e04b5616"}'::jsonb)
                          = '69771fe45faa0869e04b5616',                               'mongo_oid');
SELECT landing.etl_assert(landing.mongo_ts('{"$date":{"$numberLong":"1768471200000"}}'::jsonb)
                          = to_timestamp(1768471200),                                 'mongo_ts $date');
SELECT landing.etl_assert(landing.mongo_ts('"2026-01-26 08:03:31"'::jsonb) IS NOT NULL,
                          'mongo_ts texto ISO: usuarios lo trae asi');

\echo 'Task 1 OK'
