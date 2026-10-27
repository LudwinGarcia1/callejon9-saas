-- ============================================================
-- Verificacion manual del aislamiento multi-tenant.
--
-- Se ejecuta COMO EL ROL DE APLICACION, que es el que esta sujeto
-- a las politicas RLS:
--
--   psql -U callejon9_app -d callejon9 -f scripts\verify-rls.sql
--
-- Resultado esperado:
--   check_1..check_3 conservan las comprobaciones base de users.
--   check_5 sin tenant -> 0 sesiones y 0 movimientos.
--   check_6 tenant A -> 1 sesion propia, 0 de B y 1 movimiento propio.
--   check_7 tenant B -> 1 sesion y movimiento propios.
--   check_8 insertar una sesion de B bajo A FALLA por RLS.
--   check_9 asociar un movimiento de A a la sesion de B FALLA por FK.
--
-- Si check_1 devuelve mas de 0, o si el INSERT cruzado tiene exito,
-- el aislamiento NO funciona y no se debe seguir construyendo.
-- ============================================================

\set ON_ERROR_STOP off

-- Los tenants viven en el control plane: sin RLS, escribibles sin contexto.
INSERT INTO tenants (name, slug) VALUES ('Tenant A', 'verify-a'), ('Tenant B', 'verify-b');

-- check_1: sin tenant activo no debe verse NADA.
SELECT 'check_1  sin tenant -> ' || count(*) AS resultado FROM users;

-- check_2: sembrar un usuario dentro del tenant A y verlo.
BEGIN;
  SELECT set_config('app.tenant_id',
                    (SELECT id::text FROM tenants WHERE slug = 'verify-a'), true);
  INSERT INTO users (tenant_id, email, password_hash, full_name, role)
  VALUES ((SELECT id FROM tenants WHERE slug = 'verify-a'),
          'a@demo.com', 'x', 'Usuario A', 'ADMIN');
  SELECT 'check_2  tenant A ve -> ' || count(*) AS resultado FROM users;
COMMIT;

-- check_3: desde el tenant B, el usuario de A debe ser invisible.
BEGIN;
  SELECT set_config('app.tenant_id',
                    (SELECT id::text FROM tenants WHERE slug = 'verify-b'), true);
  SELECT 'check_3  tenant B ve -> ' || count(*) AS resultado FROM users;
COMMIT;

-- check_4: insertar a nombre de OTRO tenant debe fallar por WITH CHECK.
BEGIN;
  SELECT set_config('app.tenant_id',
                    (SELECT id::text FROM tenants WHERE slug = 'verify-b'), true);
  SELECT 'check_4  el siguiente INSERT DEBE fallar:' AS resultado;
  INSERT INTO users (tenant_id, email, password_hash, full_name, role)
  VALUES ((SELECT id FROM tenants WHERE slug = 'verify-a'),
          'intruso@demo.com', 'x', 'Intruso', 'ADMIN');
ROLLBACK;

-- Sembrar cajeros, sesiones y movimientos de cada restaurante con el rol app.
BEGIN;
  SELECT set_config('app.tenant_id',
                    (SELECT id::text FROM tenants WHERE slug = 'verify-a'), true);
  INSERT INTO users (tenant_id, email, password_hash, full_name, role)
  VALUES ((SELECT id FROM tenants WHERE slug = 'verify-a'),
          'cajero-a@verify.invalid', 'x', 'Cajero A', 'CASHIER')
  RETURNING id AS verify_cashier_a_id \gset
  INSERT INTO cash_sessions (tenant_id, cashier_id, opening_fund)
  VALUES ((SELECT id FROM tenants WHERE slug = 'verify-a'), :'verify_cashier_a_id', 100)
  RETURNING id AS verify_session_a_id \gset
  INSERT INTO cash_movements (tenant_id, cash_session_id, movement_type, amount, reason, recorded_by)
  VALUES ((SELECT id FROM tenants WHERE slug = 'verify-a'), :'verify_session_a_id',
          'DEPOSIT', 10, 'Prueba RLS', :'verify_cashier_a_id');
COMMIT;

BEGIN;
  SELECT set_config('app.tenant_id',
                    (SELECT id::text FROM tenants WHERE slug = 'verify-b'), true);
  INSERT INTO users (tenant_id, email, password_hash, full_name, role)
  VALUES ((SELECT id FROM tenants WHERE slug = 'verify-b'),
          'cajero-b@verify.invalid', 'x', 'Cajero B', 'CASHIER')
  RETURNING id AS verify_cashier_b_id \gset
  INSERT INTO cash_sessions (tenant_id, cashier_id, opening_fund)
  VALUES ((SELECT id FROM tenants WHERE slug = 'verify-b'), :'verify_cashier_b_id', 100)
  RETURNING id AS verify_session_b_id \gset
  INSERT INTO cash_movements (tenant_id, cash_session_id, movement_type, amount, reason, recorded_by)
  VALUES ((SELECT id FROM tenants WHERE slug = 'verify-b'), :'verify_session_b_id',
          'DEPOSIT', 10, 'Prueba RLS', :'verify_cashier_b_id');
COMMIT;

-- Sin tenant, ambas tablas nuevas quedan vacias para el rol de aplicacion.
SELECT 'check_5  sin tenant, sesiones -> ' || count(*) AS resultado FROM cash_sessions;
SELECT 'check_5  sin tenant, movimientos -> ' || count(*) AS resultado FROM cash_movements;

-- El tenant A ve sus filas y no las del tenant B.
BEGIN;
  SELECT set_config('app.tenant_id',
                    (SELECT id::text FROM tenants WHERE slug = 'verify-a'), true);
  SELECT 'check_6  tenant A, sesiones propias -> ' || count(*) AS resultado
    FROM cash_sessions WHERE id = :'verify_session_a_id';
  SELECT 'check_6  tenant A, sesiones de B -> ' || count(*) AS resultado
    FROM cash_sessions WHERE id = :'verify_session_b_id';
  SELECT 'check_6  tenant A, movimientos propios -> ' || count(*) AS resultado
    FROM cash_movements WHERE cash_session_id = :'verify_session_a_id';
COMMIT;

-- El tenant B ve su turno y su bitacora.
BEGIN;
  SELECT set_config('app.tenant_id',
                    (SELECT id::text FROM tenants WHERE slug = 'verify-b'), true);
  SELECT 'check_7  tenant B, sesiones propias -> ' || count(*) AS resultado
    FROM cash_sessions WHERE id = :'verify_session_b_id';
  SELECT 'check_7  tenant B, movimientos propios -> ' || count(*) AS resultado
    FROM cash_movements WHERE cash_session_id = :'verify_session_b_id';
COMMIT;

-- Estas dos inserciones DEBEN fallar: una por WITH CHECK de RLS y otra por FK compuesta.
BEGIN;
  SELECT set_config('app.tenant_id',
                    (SELECT id::text FROM tenants WHERE slug = 'verify-a'), true);
  SELECT 'check_8  el siguiente INSERT DEBE fallar por RLS:' AS resultado;
  INSERT INTO cash_sessions (tenant_id, cashier_id, opening_fund)
  VALUES ((SELECT id FROM tenants WHERE slug = 'verify-b'), :'verify_cashier_b_id', 100);
ROLLBACK;

BEGIN;
  SELECT set_config('app.tenant_id',
                    (SELECT id::text FROM tenants WHERE slug = 'verify-a'), true);
  SELECT 'check_9  el siguiente INSERT DEBE fallar por FK compuesta:' AS resultado;
  INSERT INTO cash_movements (tenant_id, cash_session_id, movement_type, amount, reason, recorded_by)
  VALUES ((SELECT id FROM tenants WHERE slug = 'verify-a'), :'verify_session_b_id',
          'DEPOSIT', 5, 'Cruce no permitido', :'verify_cashier_a_id');
ROLLBACK;

-- Limpieza: borrar los tenants arrastra los usuarios por ON DELETE CASCADE.
DELETE FROM tenants WHERE slug IN ('verify-a', 'verify-b');
