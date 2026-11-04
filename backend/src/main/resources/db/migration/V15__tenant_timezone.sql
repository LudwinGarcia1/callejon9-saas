-- ============================================================
-- Zona horaria por restaurante (CAL-19).
--
-- Hasta V14 el corte de dia salia de app.timezone, una sola zona para
-- todo el SaaS: un restaurante en Cancun (UTC-5 todo el anio) veia sus
-- ventas de las 23:00 en el dia equivocado. La zona pasa a ser un dato
-- del tenant. El default conserva el comportamiento previo para los
-- restaurantes que ya existen.
--
-- Se guarda el ID IANA (America/Cancun), nunca un offset fijo: el offset
-- depende de la fecha cuando la zona tiene horario de verano, y los
-- instantes historicos siguen en UTC para reinterpretarse con la zona
-- vigente.
--
-- La aplicacion valida el ID con java.time.ZoneId antes de escribir. El
-- trigger es la segunda barrera: si un ID que Java acepta no existe en
-- la base de tz de PostgreSQL, el AT TIME ZONE de la analitica fallaria
-- en cada consulta; aqui se rechaza al escribir y la fila conserva su
-- valor previo.
--
-- tenants es plano de control (sin RLS). V11 ya concede a callejon9_app
-- UPDATE sobre la tabla; no hace falta ningun grant nuevo.
-- ============================================================

ALTER TABLE tenants
    ADD COLUMN timezone varchar(64) NOT NULL DEFAULT 'America/Mexico_City';

CREATE FUNCTION validate_tenant_timezone() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_timezone_names WHERE name = NEW.timezone) THEN
        RAISE EXCEPTION 'unknown time zone: %', NEW.timezone
            USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER tenants_timezone_valid
    BEFORE INSERT OR UPDATE OF timezone ON tenants
    FOR EACH ROW EXECUTE FUNCTION validate_tenant_timezone();
