-- ============================================================
-- Bootstrap de la base de datos.
--
-- Dos roles con propositos distintos:
--   callejon9_owner  -> unicamente ejecuta las migraciones Flyway.
--                       Es dueno de las tablas, asi que ignoraria RLS;
--                       por eso las migraciones aplican FORCE ROW LEVEL
--                       SECURITY.
--   callejon9_app    -> runtime de la aplicacion. NO es dueno de nada y
--                       no tiene BYPASSRLS, por lo que las politicas de
--                       aislamiento si le aplican.
--
-- Uso:
--   psql -U postgres -f scripts\setup-db.sql
-- ============================================================

CREATE ROLE callejon9_owner LOGIN PASSWORD 'owner_dev_pwd';
CREATE ROLE callejon9_app   LOGIN PASSWORD 'app_dev_pwd';

CREATE DATABASE callejon9      OWNER callejon9_owner;
CREATE DATABASE callejon9_test OWNER callejon9_owner;

\connect callejon9
GRANT USAGE ON SCHEMA public TO callejon9_app;

\connect callejon9_test
GRANT USAGE ON SCHEMA public TO callejon9_app;
