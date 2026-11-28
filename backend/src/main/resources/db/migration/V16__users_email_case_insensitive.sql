-- ============================================================
-- CAL-180: el correo de un usuario no distingue mayusculas.
--
-- Hasta V15 el correo se guardaba tal como se escribio y la unicidad
-- (tenant_id, email) comparaba de forma exacta. La app movil envia el
-- correo en minusculas, asi que un usuario creado como 'Ana.Caja@...'
-- nunca podia entrar desde el telefono, y en un mismo restaurante
-- podian convivir 'mesero@...' y 'Mesero@...'.
--
-- Esta migracion deja todos los correos en su forma canonica (sin
-- espacios a los lados y en minusculas, igual que EmailAddress.normalize)
-- y agrega el indice unico sobre (tenant_id, lower(email)).
--
-- CHOQUES PREVIOS
--
-- Si un restaurante ya tiene dos usuarios cuyo correo solo cambia en
-- mayusculas, no se puede borrar ninguno: ordenes, ventas y tickets los
-- referencian. Se conserva el correo para el usuario activo mas antiguo
-- y a los demas se les antepone 'duplicado-<8 caracteres del id>-', de
-- modo que conservan su historial y el administrador puede corregirlos.
-- Cada choque se informa con RAISE NOTICE.
--
-- RLS
--
-- users tiene FORCE ROW LEVEL SECURITY y Flyway corre como el dueno,
-- asi que cada UPDATE solo ve las filas del tenant publicado en
-- app.tenant_id. Por eso se recorre cada tenant y se publica con
-- set_config(..., true), igual que en V6. La creacion del indice no
-- pasa por RLS y cubre todas las filas.
-- ============================================================

DO $$
DECLARE
    v_tenant record;
    v_renamed integer;
BEGIN
    FOR v_tenant IN SELECT id, slug FROM tenants LOOP
        PERFORM set_config('app.tenant_id', v_tenant.id::text, true);

        WITH ranked AS (
            SELECT id,
                   lower(btrim(email)) AS normalized,
                   row_number() OVER (PARTITION BY lower(btrim(email))
                                      ORDER BY active DESC, created_at, id) AS position
              FROM users
        )
        UPDATE users u
           SET email = left('duplicado-' || left(u.id::text, 8) || '-' || r.normalized, 180),
               updated_at = now()
          FROM ranked r
         WHERE u.id = r.id
           AND r.position > 1;

        GET DIAGNOSTICS v_renamed = ROW_COUNT;
        IF v_renamed > 0 THEN
            RAISE NOTICE 'CAL-180: % correo(s) duplicado(s) renombrado(s) en el tenant %',
                v_renamed, v_tenant.slug;
        END IF;

        UPDATE users
           SET email = lower(btrim(email)),
               updated_at = now()
         WHERE email <> lower(btrim(email));
    END LOOP;
END $$;

CREATE UNIQUE INDEX users_email_lower_unique_per_tenant
    ON users (tenant_id, lower(email));
