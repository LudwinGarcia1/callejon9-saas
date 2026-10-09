-- ============================================================
-- Rotacion de refresh tokens con deteccion de reutilizacion.
--
-- family_id agrupa todos los tokens que descienden de un mismo
-- login: al rotar, el token nuevo hereda la familia del consumido.
-- Si un token ya consumido vuelve a presentarse, se revoca la
-- familia completa, porque ya no hay forma de saber cual de las
-- dos copias es la legitima.
--
-- replaced_by enlaza cada token con el que lo sustituyo y deja
-- trazable la cadena de rotaciones.
--
-- El DEFAULT volatil rellena cualquier fila previa con una familia
-- propia durante la reescritura de la tabla. Se usa en lugar de un
-- UPDATE porque FORCE ROW LEVEL SECURITY tambien aplica al dueno:
-- sin app.tenant_id fijado, un UPDATE no veria ninguna fila y el
-- SET NOT NULL posterior fallaria. Despues se quita el DEFAULT para
-- que la aplicacion tenga que asignar la familia explicitamente.
--
-- No hace falta tocar la politica RLS ni los permisos: la politica
-- tenant_isolation aplica a la tabla completa y los GRANT de V4 se
-- otorgaron a nivel de tabla, no de columna.
-- ============================================================

ALTER TABLE refresh_tokens
    ADD COLUMN family_id uuid NOT NULL DEFAULT gen_random_uuid();

ALTER TABLE refresh_tokens
    ALTER COLUMN family_id DROP DEFAULT;

ALTER TABLE refresh_tokens
    ADD COLUMN replaced_by uuid REFERENCES refresh_tokens (id) ON DELETE SET NULL;

CREATE INDEX idx_refresh_tokens_tenant_family ON refresh_tokens (tenant_id, family_id);
