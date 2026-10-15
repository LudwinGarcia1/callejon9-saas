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
--   psql "host=<fqdn> port=5432 dbname=postgres user=<admin> sslmode=require" `
--        -v owner_password=<db-owner-password> -v app_password=<db-app-password> `
--        -f scripts/setup-db-azure.sql
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

-- PostgreSQL 16 exige pertenecer al rol para cederle una base. Desde la
-- version 15 el esquema public pertenece a pg_database_owner, asi que el nuevo
-- dueno puede crear tablas en el sin otro GRANT.
GRANT callejon9_owner TO CURRENT_USER;
ALTER DATABASE callejon9 OWNER TO callejon9_owner;
REVOKE callejon9_owner FROM CURRENT_USER;

GRANT USAGE ON SCHEMA public TO callejon9_app;

-- Comprobacion: ambos roles deben dar f en las dos columnas.
SELECT rolname, rolsuper, rolbypassrls
  FROM pg_roles
 WHERE rolname LIKE 'callejon9%'
 ORDER BY rolname;
