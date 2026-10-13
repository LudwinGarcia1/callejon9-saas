package com.callejon9.tenancy;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Contrato de seguridad del esquema, leido directamente del catalogo de
 * PostgreSQL despues de aplicar todas las migraciones.
 *
 * TenantIsolationTest demuestra que la politica aisla filas; esta prueba
 * demuestra que la politica sigue existiendo en todas las tablas que la
 * necesitan y que el rol de runtime no tiene una salida alrededor de ella.
 * Si una migracion agrega una tabla o vista, concede un privilegio de mas o
 * debilita RLS, el build falla nombrando el objeto.
 *
 * Al agregar una tabla o vista nueva hay que clasificarla aqui: por
 * restaurante (RLS forzado) o control plane (con la lista exacta de
 * privilegios que el codigo usa).
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Contrato de seguridad de la base de datos")
class DatabaseSecurityContractTest {

    private static final String APP_ROLE = "callejon9_app";

    private static final List<String> TABLE_PRIVILEGES = List.of(
            "SELECT", "INSERT", "UPDATE", "DELETE", "TRUNCATE", "REFERENCES", "TRIGGER");

    private static final Set<String> FULL_DML = Set.of("SELECT", "INSERT", "UPDATE", "DELETE");

    /**
     * Tablas, vistas, vistas materializadas y tablas foraneas: todas reciben
     * DML por el ALTER DEFAULT PRIVILEGES de V4, y una vista simple sobre una
     * tabla de control plane es actualizable con los privilegios de su dueno.
     */
    private static final String PUBLIC_RELATIONS = """
            SELECT c.relname
            FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
            WHERE n.nspname = 'public' AND c.relkind IN ('r', 'p', 'v', 'm', 'f')
            """;

    /** Expresion de V5, tal como la normaliza PostgreSQL en pg_policies. */
    private static final String TENANT_ISOLATION_EXPRESSION =
            "(tenant_id = (NULLIF(current_setting('app.tenant_id'::text, true), ''::text))::uuid)";

    /** Tablas con datos de un restaurante: el aislamiento lo impone su politica RLS. */
    private static final Set<String> TENANT_TABLES = Set.of(
            "users", "refresh_tokens", "restaurant_tables", "categories", "products",
            "customers", "orders", "order_items", "sales", "payments", "tickets",
            "inventory_items", "inventory_movements", "notifications");

    /**
     * Control plane, sin RLS. Cada privilegio responde a un uso real:
     * tenants se lee, se crea en el signup, se bloquea con SELECT ... FOR UPDATE
     * (que exige UPDATE) y se borra en la compensacion del onboarding; plans
     * solo se lee; subscriptions se lee y se crea en el signup (su borrado en
     * cascada lo ejecuta PostgreSQL como dueno de la tabla).
     */
    private static final Map<String, Set<String>> CONTROL_PLANE_PRIVILEGES = Map.of(
            "tenants", FULL_DML,
            "plans", Set.of("SELECT"),
            "subscriptions", Set.of("SELECT", "INSERT"),
            "flyway_schema_history", Set.of());

    /** Unica tabla de control plane que referencia un tenant sin estar bajo RLS. */
    private static final Set<String> CONTROL_PLANE_WITH_TENANT_ID = Set.of("subscriptions");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("las pruebas se conectan con el rol de runtime")
    void connectsAsRuntimeRole() {
        assertThat(jdbcTemplate.queryForObject("SELECT current_user", String.class))
                .isEqualTo(APP_ROLE);
    }

    @Test
    @DisplayName("toda tabla o vista del esquema esta clasificada")
    void everyRelationIsClassified() {
        Set<String> classified = new HashSet<>(TENANT_TABLES);
        classified.addAll(CONTROL_PLANE_PRIVILEGES.keySet());

        assertThat(jdbcTemplate.queryForList(PUBLIC_RELATIONS, String.class))
                .as("Clasifica cada relacion como TENANT_TABLES o CONTROL_PLANE_PRIVILEGES")
                .containsExactlyInAnyOrderElementsOf(classified);
    }

    @Test
    @DisplayName("toda tabla con tenant_id esta bajo RLS salvo las excepciones declaradas")
    void everyTableWithTenantIdIsTenantScoped() {
        // pg_attribute y no information_schema.columns: esta ultima oculta las
        // tablas sobre las que el rol conectado no tiene privilegios.
        List<String> tablesWithTenantId = jdbcTemplate.queryForList("""
                SELECT c.relname
                FROM pg_attribute a
                JOIN pg_class c ON c.oid = a.attrelid
                JOIN pg_namespace n ON n.oid = c.relnamespace
                WHERE n.nspname = 'public' AND c.relkind IN ('r', 'p')
                  AND a.attname = 'tenant_id' AND NOT a.attisdropped
                """, String.class);

        assertThat(tablesWithTenantId)
                .filteredOn(table -> !CONTROL_PLANE_WITH_TENANT_ID.contains(table))
                .as("Tablas con tenant_id que no estan en TENANT_TABLES")
                .allMatch(TENANT_TABLES::contains);
    }

    @Test
    @DisplayName("las tablas por restaurante tienen RLS habilitado y forzado")
    void tenantTablesEnableAndForceRls() {
        List<String> withoutForcedRls = jdbcTemplate.queryForList("""
                SELECT c.relname
                FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
                WHERE n.nspname = 'public' AND c.relkind IN ('r', 'p')
                  AND NOT (c.relrowsecurity AND c.relforcerowsecurity)
                """, String.class);

        assertThat(withoutForcedRls)
                .as("Tablas sin ENABLE + FORCE ROW LEVEL SECURITY")
                .doesNotContainAnyElementsOf(TENANT_TABLES);
    }

    @Test
    @DisplayName("las tablas por restaurante filtran lectura y escritura por app.tenant_id")
    void tenantTablesHaveTenantIsolationPolicy() {
        List<Map<String, Object>> policies = jdbcTemplate.queryForList("""
                SELECT tablename, permissive, cmd, qual, with_check
                FROM pg_policies WHERE schemaname = 'public'
                """);

        List<String> violations = new ArrayList<>();
        for (String table : new TreeSet<>(TENANT_TABLES)) {
            List<Map<String, Object>> own = policies.stream()
                    .filter(policy -> table.equals(policy.get("tablename")))
                    .toList();

            boolean isolatesAllCommands = own.stream().anyMatch(policy ->
                    "ALL".equals(policy.get("cmd"))
                            && TENANT_ISOLATION_EXPRESSION.equals(policy.get("qual"))
                            && TENANT_ISOLATION_EXPRESSION.equals(policy.get("with_check")));
            // Las politicas permisivas se combinan con OR: una sola que no sea
            // exactamente el filtro por tenant abriria la tabla completa. Se
            // compara la expresion entera, no si menciona app.tenant_id, porque
            // USING (current_setting('app.tenant_id', true) IS NOT NULL) la
            // menciona y deja ver las filas de todos los restaurantes.
            boolean everyPermissiveIsolates = own.stream()
                    .filter(policy -> "PERMISSIVE".equals(policy.get("permissive")))
                    .allMatch(policy -> TENANT_ISOLATION_EXPRESSION.equals(policy.get("qual"))
                            && (policy.get("with_check") == null
                                || TENANT_ISOLATION_EXPRESSION.equals(policy.get("with_check"))));

            if (!isolatesAllCommands || !everyPermissiveIsolates) {
                violations.add(table);
            }
        }

        assertThat(violations)
                .as("Tablas cuyas politicas no son exactamente " + TENANT_ISOLATION_EXPRESSION)
                .isEmpty();
    }

    @Test
    @DisplayName("los privilegios del rol de runtime coinciden con la matriz")
    void runtimeRolePrivilegesMatchMatrix() {
        // Se consulta por oid para no depender de como se cite el nombre.
        Map<String, Set<String>> granted = new HashMap<>();
        jdbcTemplate.query("""
                SELECT c.relname, p.privilege
                FROM pg_class c
                JOIN pg_namespace n ON n.oid = c.relnamespace
                CROSS JOIN unnest(?::text[]) AS p(privilege)
                WHERE n.nspname = 'public' AND c.relkind IN ('r', 'p', 'v', 'm', 'f')
                  AND has_table_privilege(?, c.oid, p.privilege)
                """,
                rs -> {
                    granted.computeIfAbsent(rs.getString(1), relation -> new HashSet<>())
                            .add(rs.getString(2));
                },
                TABLE_PRIVILEGES.toArray(String[]::new), APP_ROLE);

        List<String> mismatches = new ArrayList<>();
        for (String relation : new TreeSet<>(jdbcTemplate.queryForList(PUBLIC_RELATIONS, String.class))) {
            Set<String> expected = TENANT_TABLES.contains(relation)
                    ? FULL_DML
                    : CONTROL_PLANE_PRIVILEGES.getOrDefault(relation, Set.of());
            Set<String> actual = granted.getOrDefault(relation, Set.of());

            for (String privilege : TABLE_PRIVILEGES) {
                if (actual.contains(privilege) != expected.contains(privilege)) {
                    mismatches.add(relation + ": " + privilege
                            + (actual.contains(privilege) ? " (sobra)" : " (falta)"));
                }
            }
        }

        assertThat(mismatches).as("Privilegios de " + APP_ROLE + " fuera de la matriz").isEmpty();
    }

    @Test
    @DisplayName("el rol de runtime no es dueno ni puede saltarse RLS")
    void runtimeRoleCannotBypassRls() {
        Map<String, Object> role = jdbcTemplate.queryForMap("""
                SELECT rolsuper, rolbypassrls, rolcreaterole, rolcreatedb
                FROM pg_roles WHERE rolname = ?
                """, APP_ROLE);
        assertThat(role).allSatisfy((attribute, value) ->
                assertThat(value).as(attribute).isEqualTo(false));

        List<String> owned = jdbcTemplate.queryForList("""
                SELECT c.relname
                FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
                WHERE n.nspname = 'public' AND pg_get_userbyid(c.relowner) = ?
                """, String.class, APP_ROLE);
        assertThat(owned).as("Relaciones cuyo dueno es " + APP_ROLE).isEmpty();

        assertThat(jdbcTemplate.queryForObject(
                "SELECT has_schema_privilege(?, 'public', 'CREATE')", Boolean.class, APP_ROLE))
                .as("CREATE en el esquema public").isFalse();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT has_database_privilege(?, current_database(), 'CREATE')", Boolean.class,
                APP_ROLE))
                .as("CREATE en la base de datos").isFalse();

        // Una funcion SECURITY DEFINER corre con los privilegios de su dueno:
        // ejecutable por el rol de runtime, le devolveria lo que V11 le quito.
        assertThat(jdbcTemplate.queryForList("""
                SELECT p.proname
                FROM pg_proc p JOIN pg_namespace n ON n.oid = p.pronamespace
                WHERE n.nspname = 'public' AND p.prosecdef
                  AND has_function_privilege(?, p.oid, 'EXECUTE')
                """, String.class, APP_ROLE))
                .as("Funciones SECURITY DEFINER ejecutables por " + APP_ROLE)
                .isEmpty();
    }

    @Test
    @DisplayName("el rol de runtime no puede alterar planes ni el historial de migraciones")
    void runtimeRoleCannotWriteCatalogOrMigrationHistory() {
        assertPermissionDenied("UPDATE plans SET name = name WHERE code = 'FREE'");
        assertPermissionDenied("DELETE FROM plans WHERE false");
        assertPermissionDenied("UPDATE subscriptions SET status = status WHERE false");
        assertPermissionDenied("DELETE FROM flyway_schema_history WHERE false");
        assertPermissionDenied("SELECT count(*) FROM flyway_schema_history");
    }

    private void assertPermissionDenied(String sql) {
        assertThatThrownBy(() -> jdbcTemplate.execute(sql))
                .as(sql)
                .rootCause()
                .isInstanceOfSatisfying(SQLException.class, cause ->
                        assertThat(cause.getSQLState()).isEqualTo("42501"));
    }
}
