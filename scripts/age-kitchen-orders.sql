-- ============================================================
-- Envejece las comandas enviadas para que el semaforo del tablero de
-- cocina se vea en sus tres niveles.
--
-- Existe porque el semaforo mide contra sent_to_kitchen_at, que lo pone
-- el servidor al enviar. Sembrando por la API todas las comandas nacen
-- recien enviadas, asi que el tablero solo se ve en verde y los umbrales
-- de 15 y 25 minutos quedan sin comprobar. La unica forma de verlos sin
-- esperar media hora es mover el reloj en la base.
--
-- No usar fuera de desarrollo: reescribe un dato que en produccion es un
-- hecho del servicio.
--
-- Uso:
--   $env:PGPASSWORD='app_dev_pwd'
--   & "C:\Program Files\PostgreSQL\18\bin\psql.exe" -U callejon9_app -p 5432 `
--       -d callejon9 -v slug=centro -f scripts\age-kitchen-orders.sql
-- ============================================================

\set ON_ERROR_STOP on

BEGIN;

-- RLS aplica tambien al dueno (FORCE ROW LEVEL SECURITY), asi que sin
-- publicar el inquilino el UPDATE no encuentra ninguna fila y termina
-- sin error, que es peor que fallar. El tercer argumento en true lo hace
-- local a la transaccion.
SELECT set_config(
    'app.tenant_id',
    (SELECT id::text FROM tenants WHERE slug = :'slug'),
    true
) AS tenant_publicado;

WITH numeradas AS (
    SELECT id, row_number() OVER (ORDER BY sent_to_kitchen_at, id) AS n
    FROM orders
    WHERE status = 'SENT'
)
UPDATE orders o
SET sent_to_kitchen_at = now() - make_interval(mins => CASE numeradas.n % 3
        WHEN 0 THEN 4    -- por debajo de 15: normal
        WHEN 1 THEN 18   -- entre 15 y 25: demorada
        ELSE           32 -- por encima de 25: muy demorada
    END)
FROM numeradas
WHERE o.id = numeradas.id;

-- Comprobacion: si esta lista sale vacia, el inquilino no se publico o no
-- hay comandas enviadas.
SELECT folio,
       round(extract(epoch FROM now() - sent_to_kitchen_at) / 60) AS minutos,
       CASE
           WHEN now() - sent_to_kitchen_at > interval '25 minutes' THEN 'muy demorada'
           WHEN now() - sent_to_kitchen_at > interval '15 minutes' THEN 'demorada'
           ELSE 'normal'
       END AS semaforo
FROM orders
WHERE status = 'SENT'
ORDER BY sent_to_kitchen_at;

COMMIT;
