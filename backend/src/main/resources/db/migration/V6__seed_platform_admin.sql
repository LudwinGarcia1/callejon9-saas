-- ============================================================
-- Siembra del super-administrador de la plataforma.
--
-- POR QUE EXISTE UN TENANT QUE NO ES UN RESTAURANTE
--
-- users.tenant_id es NOT NULL, y el login resuelve el tenant por slug
-- ANTES de buscar al usuario, precisamente para que un correo valido en
-- un restaurante no sirva para entrar a otro. Por lo tanto un
-- SUPER_ADMIN tambien necesita pertenecer a un tenant y entrar con un
-- slug.
--
-- La alternativa habria sido hacer users.tenant_id nullable y ramificar
-- el login, lo que debilitaria la garantia de aislamiento justo en el
-- punto donde mas importa. Se prefiere un tenant tecnico: el esquema no
-- se toca y el flujo de login queda intacto. El tenant 'platform' no es
-- un restaurante y no aparece en ningun listado de restaurantes.
--
-- POR QUE HACE FALTA set_config AQUI
--
-- V3 aplico FORCE ROW LEVEL SECURITY a las 14 tablas del plano de
-- datos, y eso incluye al DUENO de la tabla. Flyway corre como
-- callejon9_owner, que es el dueno, y sin app.tenant_id activo el
-- WITH CHECK de la politica rechaza la fila con:
--
--   ERROR: new row violates row-level security policy for table "users"
--
-- Verificado contra la base antes de escribir esta migracion. Por eso
-- el INSERT en users va dentro de un bloque que publica el tenant con
-- set_config(..., true), local a la transaccion.
--
-- La tabla tenants no lleva RLS (es el catalogo de tenants), asi que su
-- INSERT no necesita nada.
--
-- Credenciales (desarrollo y demo):
--   slug:     platform
--   email:    super@callejon9.com
--   password: Callejon9Demo!
--
-- El hash se genero con la libreria bcrypt de Python, la misma que
-- usaba el sistema Flask original, lo que confirma que los hashes
-- heredados siguen siendo validos bajo Spring Security.
--
-- Idempotente: se puede reaplicar sin duplicar filas.
-- ============================================================

DO $$
DECLARE
    v_tenant_id uuid;
BEGIN
    INSERT INTO tenants (name, slug, active)
    VALUES ('Plataforma Callejon 9', 'platform', true)
    ON CONFLICT (slug) DO NOTHING;

    SELECT id INTO v_tenant_id FROM tenants WHERE slug = 'platform';

    -- Publica el tenant para que la politica RLS de users acepte la fila.
    PERFORM set_config('app.tenant_id', v_tenant_id::text, true);

    INSERT INTO users (tenant_id, email, password_hash, full_name,
                       role, active, totp_enabled)
    VALUES (v_tenant_id,
            'super@callejon9.com',
            '$2b$12$PQi8j9RGaLfN33EInKHT4uWi9ToCh6ZH9YiwQTuozkfuOa4T1URFy',
            'Super Administrador',
            'SUPER_ADMIN',
            true,
            false)
    ON CONFLICT (tenant_id, email) DO NOTHING;
END $$;
