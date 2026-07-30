-- ============================================================
-- Verificacion manual del aislamiento multi-tenant.
--
-- Se ejecuta COMO EL ROL DE APLICACION, que es el que esta sujeto
-- a las politicas RLS:
--
--   psql -U callejon9_app -d callejon9 -f scripts\verify-rls.sql
--
-- Resultado esperado, exactamente:
--   check_1  sin tenant -> 0
--   check_2  tenant A ve -> 1
--   check_3  tenant B ve -> 0
--   check_4  el INSERT cruzado FALLA con
--            "new row violates row-level security policy"
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

-- Limpieza: borrar los tenants arrastra los usuarios por ON DELETE CASCADE.
DELETE FROM tenants WHERE slug IN ('verify-a', 'verify-b');
