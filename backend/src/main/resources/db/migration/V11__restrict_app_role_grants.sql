-- ============================================================
-- Minimo privilegio para el rol de runtime en el plano de control.
--
-- V4 concedio SELECT, INSERT, UPDATE y DELETE sobre todas las tablas.
-- En el plano por restaurante eso es correcto: la politica RLS decide
-- que filas ve y escribe callejon9_app. Pero el plano de control no
-- tiene RLS, asi que ahi el privilegio de tabla es la unica barrera, y
-- V4 dejaba que la aplicacion reescribiera precios y limites de los
-- planes, editara suscripciones ajenas o alterara flyway_schema_history
-- (que tambien quedo incluida en ALL TABLES).
--
-- Se conserva solo lo que el codigo usa:
--   tenants        DML completo: login, signup, SELECT ... FOR UPDATE
--                  (exige UPDATE) y la compensacion del onboarding.
--   subscriptions  SELECT, INSERT: limites del plan y signup. El borrado
--                  en cascada desde tenants lo ejecuta PostgreSQL como
--                  dueno de la tabla, no como callejon9_app.
--   plans          SELECT: catalogo de solo lectura.
--   flyway_schema_history  nada: solo lo escribe callejon9_owner.
--
-- DatabaseSecurityContractTest exige esta matriz contra el catalogo.
-- ============================================================

REVOKE ALL ON flyway_schema_history FROM callejon9_app;

REVOKE INSERT, UPDATE, DELETE ON plans FROM callejon9_app;

REVOKE UPDATE, DELETE ON subscriptions FROM callejon9_app;
