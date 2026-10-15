-- Migracion invalida a proposito para StartupFailureTest (CAL-22): repite la
-- version 1 de db/migration. Flyway la rechaza al validar, antes de escribir
-- nada en la base, y el arranque debe fallar.
SELECT 1;
