\set ON_ERROR_STOP on

SELECT set_config('app.tenant_id',
    (SELECT id::text FROM tenants WHERE slug = :'slug'), false);

-- El onboarding crea 1 administrador; la migracion suma 6.
SELECT landing.etl_assert((SELECT count(*) FROM users) = 7, 'users: 1 del alta + 6 migrados');

SELECT landing.etl_assert(
    (SELECT count(*) FROM landing.id_map WHERE coleccion = 'usuarios') = 6,
    'id_map debe registrar los 6 usuarios');

-- Los roles se derivaron de los campos discriminantes del volcado.
SELECT landing.etl_assert((SELECT count(*) FROM users WHERE role = 'WAITER')  = 1, 'un mesero (rol 2)');
SELECT landing.etl_assert((SELECT count(*) FROM users WHERE role = 'KITCHEN') = 2, 'dos de cocina (rol 3)');
SELECT landing.etl_assert((SELECT count(*) FROM users WHERE role = 'CASHIER') = 2, 'dos de caja (rol 4)');

-- Anonimizacion: ni un correo real, ni un rastro del dominio original.
SELECT landing.etl_assert(
    (SELECT count(*) FROM users WHERE email LIKE '%@gmail.com' OR email LIKE '%@admin.com') = 0,
    'ningun correo real debe sobrevivir a la anonimizacion');

SELECT landing.etl_assert(
    (SELECT count(*) FROM users WHERE email LIKE 'usuario%@staging.local') = 6,
    'los 6 migrados llevan identidad sintetica');

-- Determinismo: el orden va por _id, y el primero por _id es siempre el mismo.
SELECT landing.etl_assert(
    (SELECT new_uuid FROM landing.id_map
      WHERE coleccion = 'usuarios' ORDER BY legacy_oid LIMIT 1)
    = (SELECT id FROM users WHERE email = 'usuario01@staging.local'),
    'usuario01 corresponde al menor _id');

\echo 'Task 2 OK'
