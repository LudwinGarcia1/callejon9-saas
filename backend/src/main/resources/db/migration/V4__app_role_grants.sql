-- ============================================================
-- Permisos del rol de runtime.
--
-- callejon9_app recibe DML sobre todas las tablas pero NO es dueno
-- de ninguna, por lo que las politicas RLS si le aplican. Esta
-- separacion es la que hace que el aislamiento sea real y no
-- decorativo: un rol dueno se saltaria las politicas.
-- ============================================================

GRANT USAGE ON SCHEMA public TO callejon9_app;

GRANT SELECT, INSERT, UPDATE, DELETE
    ON ALL TABLES IN SCHEMA public
    TO callejon9_app;

GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO callejon9_app;

-- Que los objetos creados por futuras migraciones hereden estos permisos.
ALTER DEFAULT PRIVILEGES FOR ROLE callejon9_owner IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO callejon9_app;

ALTER DEFAULT PRIVILEGES FOR ROLE callejon9_owner IN SCHEMA public
    GRANT USAGE, SELECT ON SEQUENCES TO callejon9_app;
