-- Aterrizaje del volcado: el origen entra tal cual y se transforma despues.
--
-- Preservarlo integro no es ceremonia: el criterio de validacion compara
-- origen contra destino, y si el origen solo existe dentro de un script que
-- ya corrio, esa comparacion no se puede repetir manana.
--
-- Lo ejecuta callejon9_owner: crea objetos, y callejon9_app no es dueno de
-- nada a proposito.

\set ON_ERROR_STOP on

CREATE SCHEMA IF NOT EXISTS landing;

-- --- Extended JSON -----------------------------------------------------
-- bsondump emite {"$numberInt":"95"} en vez de 95. Estas tres funciones
-- desenvuelven las formas que aparecen en este volcado; cada una acepta
-- tambien el escalar plano porque el volcado mezcla ambas.

CREATE OR REPLACE FUNCTION landing.mongo_num(v jsonb) RETURNS numeric
LANGUAGE sql IMMUTABLE AS $$
    SELECT CASE
        WHEN v IS NULL OR v = 'null'::jsonb   THEN NULL
        WHEN jsonb_typeof(v) = 'number'       THEN v::text::numeric
        WHEN v ? '$numberInt'                 THEN (v->>'$numberInt')::numeric
        WHEN v ? '$numberLong'                THEN (v->>'$numberLong')::numeric
        WHEN v ? '$numberDouble'              THEN (v->>'$numberDouble')::numeric
        WHEN v ? '$numberDecimal'             THEN (v->>'$numberDecimal')::numeric
        ELSE NULL
    END
$$;

CREATE OR REPLACE FUNCTION landing.mongo_ts(v jsonb) RETURNS timestamptz
LANGUAGE sql IMMUTABLE AS $$
    SELECT CASE
        WHEN v IS NULL OR v = 'null'::jsonb THEN NULL
        WHEN v ? '$date'                    THEN to_timestamp(landing.mongo_num(v->'$date') / 1000.0)
        WHEN jsonb_typeof(v) = 'string'     THEN (v #>> '{}')::timestamptz
        ELSE NULL
    END
$$;

CREATE OR REPLACE FUNCTION landing.mongo_oid(v jsonb) RETURNS text
LANGUAGE sql IMMUTABLE AS $$
    SELECT CASE
        WHEN v IS NULL OR v = 'null'::jsonb THEN NULL
        WHEN v ? '$oid'                     THEN v->>'$oid'
        WHEN jsonb_typeof(v) = 'string'     THEN v #>> '{}'
        ELSE NULL
    END
$$;

-- --- Aserciones ---------------------------------------------------------
-- Lanza excepcion en vez de devolver una fila: con ON_ERROR_STOP=1 eso
-- convierte un archivo .sql en una prueba con codigo de salida.

CREATE OR REPLACE FUNCTION landing.etl_assert(ok boolean, message text) RETURNS void
LANGUAGE plpgsql IMMUTABLE AS $$
BEGIN
    IF ok IS NOT TRUE THEN
        RAISE EXCEPTION 'ETL assert failed: %', message;
    END IF;
END
$$;

-- --- Tablas de aterrizaje ----------------------------------------------

DROP TABLE IF EXISTS landing.usuarios, landing.productos, landing.mesas,
                     landing.comandas, landing.insumos,
                     landing.movimientos_inventario CASCADE;

CREATE TABLE landing.usuarios               (id bigserial PRIMARY KEY, doc jsonb NOT NULL, loaded_at timestamptz NOT NULL DEFAULT now());
CREATE TABLE landing.productos              (id bigserial PRIMARY KEY, doc jsonb NOT NULL, loaded_at timestamptz NOT NULL DEFAULT now());
CREATE TABLE landing.mesas                  (id bigserial PRIMARY KEY, doc jsonb NOT NULL, loaded_at timestamptz NOT NULL DEFAULT now());
CREATE TABLE landing.comandas               (id bigserial PRIMARY KEY, doc jsonb NOT NULL, loaded_at timestamptz NOT NULL DEFAULT now());
CREATE TABLE landing.insumos                (id bigserial PRIMARY KEY, doc jsonb NOT NULL, loaded_at timestamptz NOT NULL DEFAULT now());
CREATE TABLE landing.movimientos_inventario (id bigserial PRIMARY KEY, doc jsonb NOT NULL, loaded_at timestamptz NOT NULL DEFAULT now());

-- --- Mapa de identidad --------------------------------------------------
-- Sin esto, "de que documento salio esta fila" no tiene respuesta despues
-- del cutover, y el rollback quirurgico es imposible.

CREATE TABLE IF NOT EXISTS landing.id_map (
    coleccion  text NOT NULL,
    legacy_oid text NOT NULL,
    new_uuid   uuid NOT NULL,
    PRIMARY KEY (coleccion, legacy_oid)
);

-- callejon9_app corre la transformacion: lee el aterrizaje y escribe el mapa.
GRANT USAGE ON SCHEMA landing TO callejon9_app;
GRANT SELECT ON ALL TABLES IN SCHEMA landing TO callejon9_app;
GRANT SELECT, INSERT, DELETE ON landing.id_map TO callejon9_app;
GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA landing TO callejon9_app;
