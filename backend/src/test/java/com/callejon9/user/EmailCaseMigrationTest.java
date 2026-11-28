package com.callejon9.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * V16 (CAL-180) sobre datos que ya existian: normaliza los correos de cada
 * restaurante bajo FORCE RLS, resuelve los que solo cambiaban en mayusculas
 * sin borrar a nadie y deja el indice unico sobre {@code lower(email)}.
 */
class EmailCaseMigrationTest {

    @Test
    @DisplayName("V16 normaliza correos por restaurante, renombra choques y rechaza duplicados por mayusculas")
    void upgradeNormalizesEmailsPerTenantAndResolvesCaseCollisions() throws Exception {
        try (var database = new PostgreSQLContainer<>("postgres:16-alpine")
                .withDatabaseName("migration_test").withUsername("postgres")
                .withPassword(UUID.randomUUID().toString())) {
            database.start();
            var ownerPassword = UUID.randomUUID().toString();
            try (var connection = DriverManager.getConnection(database.getJdbcUrl(), database.getUsername(), database.getPassword());
                    var statement = connection.createStatement()) {
                statement.execute("CREATE ROLE callejon9_owner LOGIN NOSUPERUSER NOBYPASSRLS PASSWORD '" + ownerPassword + "'");
                statement.execute("CREATE ROLE callejon9_app NOSUPERUSER NOBYPASSRLS");
                statement.execute("ALTER DATABASE migration_test OWNER TO callejon9_owner");
                statement.execute("ALTER SCHEMA public OWNER TO callejon9_owner");
            }
            Flyway.configure().dataSource(database.getJdbcUrl(), "callejon9_owner", ownerPassword)
                    .target("15").load().migrate();

            var first = UUID.randomUUID();
            var second = UUID.randomUUID();
            var original = UUID.randomUUID();
            var lateDuplicate = UUID.randomUUID();
            try (var connection = owner(database, ownerPassword)) {
                insertTenant(connection, first, "correo-uno");
                insertTenant(connection, second, "correo-dos");
                // Restaurante uno: dos usuarios que solo cambian en mayusculas
                // (el mas antiguo debe conservar el correo) y uno con espacios.
                insertUser(connection, first, original, " Ana.Caja@Correo.com ", "2026-10-01");
                insertUser(connection, first, lateDuplicate, "ana.caja@correo.com", "2026-10-05");
                insertUser(connection, first, UUID.randomUUID(), "Mesero@Correo.com", "2026-10-02");
                // Restaurante dos: el mismo correo es otro usuario, no un choque.
                insertUser(connection, second, UUID.randomUUID(), "ANA.CAJA@correo.com", "2026-10-03");
                connection.commit();
            }

            Flyway.configure().dataSource(database.getJdbcUrl(), "callejon9_owner", ownerPassword).load().migrate();

            try (var connection = owner(database, ownerPassword)) {
                assertThat(emails(connection, first)).containsExactly(
                        "ana.caja@correo.com",
                        "mesero@correo.com",
                        "duplicado-" + lateDuplicate.toString().substring(0, 8) + "-ana.caja@correo.com");
                assertThat(emailOf(connection, first, original)).isEqualTo("ana.caja@correo.com");
                assertThat(emails(connection, second)).containsExactly("ana.caja@correo.com");

                assertThatThrownBy(() -> insertUser(connection, first, UUID.randomUUID(), "MESERO@correo.com", "2026-11-01"))
                        .isInstanceOf(SQLException.class)
                        .hasMessageContaining("users_email_lower_unique_per_tenant");
                connection.rollback();
            }
        }
    }

    private static Connection owner(PostgreSQLContainer<?> database, String password) throws SQLException {
        var connection = DriverManager.getConnection(database.getJdbcUrl(), "callejon9_owner", password);
        connection.setAutoCommit(false);
        return connection;
    }

    private static void insertTenant(Connection connection, UUID id, String slug) throws SQLException {
        try (var statement = connection.prepareStatement("INSERT INTO tenants(id,name,slug) VALUES (?,?,?)")) {
            statement.setObject(1, id);
            statement.setString(2, "Correo " + slug);
            statement.setString(3, slug);
            statement.execute();
        }
    }

    private static void insertUser(Connection connection, UUID tenant, UUID id, String email, String createdAt)
            throws SQLException {
        publishTenant(connection, tenant);
        try (var statement = connection.prepareStatement(
                "INSERT INTO users(id,tenant_id,email,password_hash,full_name,role,created_at) "
                        + "VALUES (?,?,?,'x','Usuario','WAITER',?::timestamptz)")) {
            statement.setObject(1, id);
            statement.setObject(2, tenant);
            statement.setString(3, email);
            statement.setString(4, createdAt);
            statement.execute();
        }
    }

    private static List<String> emails(Connection connection, UUID tenant) throws SQLException {
        publishTenant(connection, tenant);
        var result = new ArrayList<String>();
        try (var statement = connection.createStatement();
                var rows = statement.executeQuery("SELECT email FROM users ORDER BY created_at")) {
            while (rows.next()) {
                result.add(rows.getString(1));
            }
        }
        return result;
    }

    private static String emailOf(Connection connection, UUID tenant, UUID user) throws SQLException {
        publishTenant(connection, tenant);
        try (var statement = connection.prepareStatement("SELECT email FROM users WHERE id = ?")) {
            statement.setObject(1, user);
            try (var rows = statement.executeQuery()) {
                return rows.next() ? rows.getString(1) : null;
            }
        }
    }

    private static void publishTenant(Connection connection, UUID tenant) throws SQLException {
        try (var statement = connection.prepareStatement("SELECT set_config('app.tenant_id', ?, true)")) {
            statement.setString(1, tenant.toString());
            statement.execute();
        }
    }
}
