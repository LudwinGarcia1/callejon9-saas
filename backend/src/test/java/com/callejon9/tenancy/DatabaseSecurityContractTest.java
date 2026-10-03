package com.callejon9.tenancy;

import java.sql.SQLException;
import java.util.ArrayList;
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
 * Si una migracion agrega una tabla, concede un privilegio de mas o debilita
 * RLS, el build falla nombrando la tabla.
 *
 * Al agregar una tabla nueva hay que clasificarla aqui: por restaurante
 * (RLS forzado) o control plane (con la lista exacta de privilegios que el
 * codigo usa).
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Contrato de seguridad de la base de datos")
class DatabaseSecurityContractTest {

    private static final String APP_ROLE = "callejon9_app";

    private static final List<String> TABLE_PRIVILEGES = List.of(
            "SELECT", "INSERT", "UPDATE", "DELETE", "TRUNCATE", "REFERENCES", "TRIGGER");

    private static final Set<String> FULL_DML = Set.of("SELECT", "INSERT", "UPDATE", "DELETE");

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
    @DisplayName("toda tabla del esquema esta clasificada")
    void everyTableIsClassified() {
        Set<String> classified = new HashSet<>(TENANT_TABLES);
        classified.addAll(CONTROL_PLANE_PRIVILEGES.keySet());

        assertThat(publicTables())
                .as("Clasifica cada tabla como TENANT_TABLES o CONTROL_PLANE_PRIVILEGES")
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
                            && filtersByTenant(policy.get("qual"))
                            && filtersByTenant(policy.get("with_check")));
            // Las politicas permisivas se combinan con OR: una sola que no
            // filtre por tenant abriria la tabla completa.
            boolean everyPermissiveFilters = own.stream()
                    .filter(policy -> "PERMISSIVE".equals(policy.get("permissive")))
                    .allMatch(policy -> filtersByTenant(policy.get("qual")));

            if (!isolatesAllCommands || !everyPermissiveFilters) {
                violations.add(table);
            }
        }

        assertThat(violations)
                .as("Tablas sin politica USING + WITH CHECK sobre app.tenant_id")
                .isEmpty();
    }

    @Test
    @DisplayName("los privilegios del rol de runtime coinciden con la matriz")
    void runtimeRolePrivilegesMatchMatrix() {
        List<String> mismatches = new ArrayList<>();
        for (String table : new TreeSet<>(publicTables())) {
            Set<String> expected = TENANT_TABLES.contains(table)
                    ? FULL_DML
                    : CONTROL_PLANE_PRIVILEGES.getOrDefault(table, Set.of());

            for (String privilege : TABLE_PRIVILEGES) {
                boolean granted = Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                        "SELECT has_table_privilege(?, ?, ?)", Boolean.class,
                        APP_ROLE, "public." + table, privilege));
                if (granted != expected.contains(privilege)) {
                    mismatches.add(table + ": " + privilege + (granted ? " (sobra)" : " (falta)"));
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

    private List<String> publicTables() {
        return jdbcTemplate.queryForList("""
                SELECT c.relname
                FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
                WHERE n.nspname = 'public' AND c.relkind IN ('r', 'p')
                """, String.class);
    }

    private static boolean filtersByTenant(Object expression) {
        return expression != null && expression.toString().contains("app.tenant_id");
    }
}
