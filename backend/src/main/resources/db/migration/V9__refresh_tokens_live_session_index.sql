-- ============================================================
-- Indice para verificar la sesion en cada peticion autenticada.
--
-- Cada access token cita su sesion (family_id) y el backend
-- comprueba en cada peticion que la familia conserve un token
-- vivo. Las filas revocadas se guardan hasta que la sesion vence,
-- porque son las que delatan una reutilizacion: una familia acumula
-- una fila por rotacion (unas cuatro por hora, cientos en 7 dias).
--
-- El indice de V8 sobre (tenant_id, family_id) obliga a recorrer
-- todas esas filas para encontrar la unica sin revocar. Este indice
-- parcial solo contiene filas con revoked_at IS NULL, que son a lo
-- sumo una por sesion, asi que el costo por peticion ya no crece con
-- la antiguedad de la sesion. Tambien sirve al UPDATE que revoca la
-- familia, que filtra por la misma condicion.
--
-- tenant_id va primero porque la politica RLS siempre agrega ese
-- predicado. No hace falta tocar la politica ni los permisos.
-- ============================================================

CREATE INDEX idx_refresh_tokens_live_session
    ON refresh_tokens (tenant_id, family_id)
    WHERE revoked_at IS NULL;
