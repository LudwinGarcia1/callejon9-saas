-- ============================================================
-- Bootstrap de roles en Azure Database for PostgreSQL Flexible Server.
--
-- Equivalente a scripts/setup-db.sql para la nube. La base `callejon9` ya
-- existe (la crea Terraform), y aqui no hay contrasenas escritas: llegan como
-- variables de psql desde Key Vault.
--
--   callejon9_owner -> solo ejecuta las migraciones Flyway y es dueno de las
--                      tablas; las migraciones aplican FORCE ROW LEVEL SECURITY.
--   callejon9_app   -> runtime. No es dueno de nada y no tiene BYPASSRLS: las
--                      politicas de aislamiento si le aplican.
--
-- Se ejecuta una sola vez, como el administrador del servidor, desde la IP
-- permitida por `operator_ip_address` (ver infra/terraform/azure-backend/README.md):
--
--   psql -v owner_password=<db-owner-password> -v app_password=<db-app-password> `
--        -f scripts/setup-db-azure.sql `
--        "host=<fqdn> port=5432 dbname=postgres user=<admin> sslmode=require"
--
-- Las opciones van ANTES de la cadena de conexion: en Windows, psql deja de
-- leer opciones despues del primer argumento posicional y trata el resto como
-- texto, asi que -v y -f se ignorarian y los valores podrian acabar en la salida.
-- ============================================================

\set ON_ERROR_STOP on

CREATE ROLE callejon9_owner LOGIN PASSWORD :'owner_password';
CREATE ROLE callejon9_app   LOGIN PASSWORD :'app_password';

\connect callejon9

-- V1__control_plane.sql ejecuta CREATE EXTENSION IF NOT EXISTS "pgcrypto".
-- Se crea antes de ceder la base: despues el administrador ya no tiene CREATE
-- sobre ella. La migracion la encuentra y no hace nada. Requiere que Terraform
-- haya permitido la extension (azure.extensions = PGCRYPTO).
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- PostgreSQL 16 exige pertenecer al rol para cederle una base o un esquema.
--
-- En un PostgreSQL local el esquema public pertenece a pg_database_owner, es
-- decir, al dueno de la base. En Azure pertenece a azure_pg_admin, asi que
-- ceder la base no basta: sin el ALTER SCHEMA, Flyway falla al crear su tabla
-- con "permission denied for schema public". Con el, Azure queda igual que el
-- entorno local y V4 puede conceder USAGE sobre public a callejon9_app.
GRANT callejon9_owner TO CURRENT_USER;
ALTER DATABASE callejon9 OWNER TO callejon9_owner;
ALTER SCHEMA public OWNER TO callejon9_owner;
REVOKE callejon9_owner FROM CURRENT_USER;

GRANT USAGE ON SCHEMA public TO callejon9_app;

-- Comprobacion: ambos roles deben dar f en las dos columnas.
SELECT rolname, rolsuper, rolbypassrls
  FROM pg_roles
 WHERE rolname LIKE 'callejon9%'
 ORDER BY rolname;

-- Comprobacion: el dueno de public debe ser callejon9_owner.
SELECT nspname, pg_get_userbyid(nspowner) AS schema_owner
  FROM pg_namespace
 WHERE nspname = 'public';
