-- Transformacion del volcado al esquema del SaaS.
--
-- Corre como callejon9_app, que no es dueno de ninguna tabla y por tanto
-- queda sujeto a RLS. Es deliberado: si la migracion escribiera por fuera
-- del aislamiento, desmentiria justo la garantia que el proyecto defiende.
--
-- Parametros:
--   -v slug=<slug del inquilino>       obligatorio, debe existir
--   -v anonymize=<true|false>          por defecto true
--   -v staging_password=<contrasena>   se hashea aqui con pgcrypto

\set ON_ERROR_STOP on
\if :{?anonymize} \else \set anonymize true \endif

BEGIN;

-- El inquilino debe existir. Crearlo aqui forkearia TenantOnboardingService,
-- que ademas crea suscripcion y limites de plan de forma atomica.
DO $$
DECLARE slug_param text := current_setting('etl.slug', true);
BEGIN
    IF NOT EXISTS (SELECT 1 FROM tenants WHERE slug = slug_param) THEN
        RAISE EXCEPTION
            'El inquilino "%" no existe. Crealo con POST /api/v1/signup antes de migrar.',
            slug_param;
    END IF;
END
$$;

SELECT set_config('app.tenant_id',
    (SELECT id::text FROM tenants WHERE slug = current_setting('etl.slug')), true);

-- La transformacion no es idempotente: escribe filas nuevas cada vez. Si
-- id_map trae algo, es que ya se corrio y no se revirtio. Sin esta guarda el
-- fallo llegaria como una violacion de clave primaria en id_map, que no dice
-- nada sobre la causa ni sobre el remedio.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM landing.id_map) THEN
        RAISE EXCEPTION
            'Ya hay una migracion cargada (% filas en id_map). Ejecuta rollback.sql antes de repetir.',
            (SELECT count(*) FROM landing.id_map);
    END IF;
END
$$;

-- --- users --------------------------------------------------------------
-- El uuid se genera por adelantado en el CTE para poder escribirlo a la vez
-- en users y en id_map. Con INSERT ... RETURNING habria que reconstruir la
-- correspondencia por un campo de negocio, que es fragil.
--
-- El volcado trae la contrasena en texto plano (usuario_clave: "mesero123"),
-- no un hash. Asi que no hay nada que preservar: se escribe el hash de la
-- contrasena de staging, hasheada aqui con pgcrypto: emite $2a$10$, que es
-- lo que espera el BCryptPasswordEncoder de Spring, y cada usuario recibe su
-- propia sal como si se hubiera dado de alta por la aplicacion.
-- usuario_tokensession se descarta por ser un secreto de sesion legible.

WITH src AS (
    SELECT doc,
           landing.mongo_oid(doc->'_id')                              AS oid,
           gen_random_uuid()                                          AS new_id,
           row_number() OVER (ORDER BY landing.mongo_oid(doc->'_id')) AS n
    FROM landing.usuarios
),
inserted AS (
    INSERT INTO users (id, tenant_id, email, password_hash, full_name,
                       role, active, totp_enabled, created_at, updated_at)
    SELECT
        s.new_id,
        current_setting('app.tenant_id')::uuid,
        CASE WHEN :anonymize
             THEN format('usuario%s@staging.local', lpad(s.n::text, 2, '0'))
             ELSE s.doc->>'usuario_email' END,
        crypt(:'staging_password', gen_salt('bf', 10)),
        CASE WHEN :anonymize
             THEN format('Usuario %s', lpad(s.n::text, 2, '0'))
             ELSE concat_ws(' ', s.doc->>'usuario_nombre', s.doc->>'usuario_apellidos') END,
        CASE s.doc->>'usuario_rol'
             WHEN '1' THEN 'ADMIN'
             WHEN '2' THEN 'WAITER'
             WHEN '3' THEN 'KITCHEN'
             WHEN '4' THEN 'CASHIER'
        END,
        coalesce(landing.mongo_num(s.doc->'usuario_status') = 1, true),
        coalesce((s.doc->>'2fa_enabled')::boolean, false),
        coalesce(landing.mongo_ts(s.doc->'created_at'), now()),
        now()
    FROM src s
    RETURNING id
)
INSERT INTO landing.id_map (coleccion, legacy_oid, new_uuid)
SELECT 'usuarios', s.oid, s.new_id FROM src s;

-- Un rol sin mapear entraria como NULL y violaria el NOT NULL, pero el
-- mensaje seria incomprensible. Este falla diciendo cual.
DO $$
DECLARE desconocidos text;
BEGIN
    SELECT string_agg(DISTINCT doc->>'usuario_rol', ', ')
      INTO desconocidos
      FROM landing.usuarios
     WHERE doc->>'usuario_rol' NOT IN ('1','2','3','4');
    IF desconocidos IS NOT NULL THEN
        RAISE EXCEPTION 'Roles sin mapear en el volcado: %', desconocidos;
    END IF;
END
$$;

COMMIT;
