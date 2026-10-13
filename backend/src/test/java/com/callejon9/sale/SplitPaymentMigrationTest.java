package com.callejon9.sale;

import java.sql.DriverManager;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import static org.assertj.core.api.Assertions.assertThat;

class SplitPaymentMigrationTest {
    @Test
    void upgradeBackfillsBothRestaurantsUnderForcedRlsWithoutDuplicatingExistingPayments() throws Exception {
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
                    .target("7").load().migrate();
            var tenants = new UUID[] { UUID.randomUUID(), UUID.randomUUID() };
            try (var connection = DriverManager.getConnection(database.getJdbcUrl(), "callejon9_owner", ownerPassword)) {
                connection.setAutoCommit(false);
                for (int i = 0; i < tenants.length; i++) {
                    var order = UUID.randomUUID();
                    var sale = UUID.randomUUID();
                    try (var statement = connection.createStatement()) {
                        statement.execute("INSERT INTO tenants(id,name,slug) VALUES ('" + tenants[i] + "','Migration','migration-" + i + "')");
                        statement.execute("SELECT set_config('app.tenant_id','" + tenants[i] + "',true)");
                        statement.execute("INSERT INTO orders(id,tenant_id,folio,status,total) VALUES ('" + order + "','" + tenants[i] + "','O-1','PAID',500)");
                        statement.execute("INSERT INTO sales(id,tenant_id,order_id,status,payment_method,subtotal,total) VALUES ('" + sale + "','" + tenants[i] + "','" + order + "','COMPLETED','CASH',500,500)");
                        statement.execute("INSERT INTO tickets(tenant_id,sale_id,order_id,folio,items_snapshot,subtotal,total,payment_method,closed_at) VALUES ('" + tenants[i] + "','" + sale + "','" + order + "','T-1','[]',500,500,'CASH',now())");
                        if (i == 1) statement.execute("INSERT INTO payments(tenant_id,sale_id,provider,method,amount,status) VALUES ('" + tenants[i] + "','" + sale + "','MANUAL','CASH',500,'COMPLETED')");
                    }
                }
                connection.commit();
            }
            Flyway.configure().dataSource(database.getJdbcUrl(), "callejon9_owner", ownerPassword).load().migrate();
            try (var connection = DriverManager.getConnection(database.getJdbcUrl(), "callejon9_owner", ownerPassword)) {
                connection.setAutoCommit(false);
                for (int i = 0; i < tenants.length; i++) {
                    try (var statement = connection.createStatement()) {
                        statement.execute("SELECT set_config('app.tenant_id','" + tenants[i] + "',true)");
                        try (var rows = statement.executeQuery("SELECT count(*),sum(amount),sum(received_amount),min(provider) FROM payments")) {
                            assertThat(rows.next()).isTrue();
                            assertThat(rows.getInt(1)).isEqualTo(1);
                            assertThat(rows.getBigDecimal(2)).isEqualByComparingTo("500");
                            assertThat(rows.getBigDecimal(3)).isEqualByComparingTo("500");
                            assertThat(rows.getString(4)).isEqualTo(i == 0 ? "LEGACY" : "MANUAL");
                        }
                        try (var rows = statement.executeQuery("SELECT jsonb_array_length(payments_snapshot),payments_snapshot->0->>'receivedAmount',change FROM tickets")) {
                            assertThat(rows.next()).isTrue();
                            assertThat(rows.getInt(1)).isEqualTo(1);
                            assertThat(new java.math.BigDecimal(rows.getString(2))).isEqualByComparingTo("500");
                            assertThat(rows.getBigDecimal(3)).isZero();
                        }
                        try (var rows = statement.executeQuery("SELECT count(*) FROM pg_class WHERE relrowsecurity AND relforcerowsecurity AND relnamespace='public'::regnamespace")) {
                            rows.next();
                            assertThat(rows.getInt(1)).isEqualTo(14);
                        }
                    }
                }
                connection.rollback();
            }
        }
    }
}
