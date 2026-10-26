package com.callejon9.tenancy;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Evidencia central del proyecto: el aislamiento entre restaurantes lo impone
 * PostgreSQL, no la aplicacion.
 *
 * Todas las escrituras pasan por el rol callejon9_app, que no es dueno de las
 * tablas y por lo tanto queda sujeto a las politicas RLS.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Aislamiento multi-tenant impuesto por RLS")
class TenantIsolationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private UUID tenantA;
    private UUID tenantB;
    private UUID cashierA;
    private UUID cashierB;
    private UUID sessionA;
    private UUID sessionB;

    @BeforeEach
    void seedTenants() {
        // La tabla tenants es control plane: no tiene RLS, se puede escribir sin contexto.
        tenantA = jdbcTemplate.queryForObject(
                "INSERT INTO tenants (name, slug) VALUES ('Tenant A', 'tenant-a') RETURNING id",
                UUID.class);
        tenantB = jdbcTemplate.queryForObject(
                "INSERT INTO tenants (name, slug) VALUES ('Tenant B', 'tenant-b') RETURNING id",
                UUID.class);

        cashierA = insertUserAs(tenantA, "a@demo.com", "Usuario A");
        cashierB = insertUserAs(tenantB, "b@demo.com", "Usuario B");
        sessionA = insertCashSessionAs(tenantA, cashierA);
        sessionB = insertCashSessionAs(tenantB, cashierB);
    }

    @AfterEach
    void cleanUp() {
        TenantContext.clear();
        // Sin contexto de tenant, RLS impide borrar users; se borra en cascada
        // eliminando los tenants, que no estan protegidos por RLS.
        jdbcTemplate.update("DELETE FROM tenants WHERE slug IN ('tenant-a','tenant-b')");
    }

    private UUID insertUserAs(UUID tenantId, String email, String name) {
        TenantContext.set(tenantId);
        try {
            return transactionTemplate.execute(status -> jdbcTemplate.queryForObject("""
                    INSERT INTO users (tenant_id, email, password_hash, full_name, role)
                    VALUES (?, ?, 'x', ?, 'ADMIN') RETURNING id
                    """, UUID.class, tenantId, email, name));
        } finally {
            TenantContext.clear();
        }
    }

    private UUID insertCashSessionAs(UUID tenantId, UUID cashierId) {
        TenantContext.set(tenantId);
        try {
            return transactionTemplate.execute(status -> jdbcTemplate.queryForObject("""
                    INSERT INTO cash_sessions (tenant_id, cashier_id, opening_fund)
                    VALUES (?, ?, 100) RETURNING id
                    """, UUID.class, tenantId, cashierId));
        } finally {
            TenantContext.clear();
        }
    }

    private List<String> readEmailsAs(UUID tenantId) {
        TenantContext.set(tenantId);
        try {
            return transactionTemplate.execute(status ->
                    jdbcTemplate.queryForList("SELECT email FROM users ORDER BY email", String.class));
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    @DisplayName("cada tenant ve unicamente sus propias filas")
    void eachTenantSeesOnlyItsOwnRows() {
        assertThat(readEmailsAs(tenantA)).containsExactly("a@demo.com");
        assertThat(readEmailsAs(tenantB)).containsExactly("b@demo.com");
    }

    @Test
    @DisplayName("sin tenant activo no se ve ninguna fila")
    void withoutTenantContextNoRowsAreVisible() {
        TenantContext.clear();

        List<String> emails = transactionTemplate.execute(status ->
                jdbcTemplate.queryForList("SELECT email FROM users", String.class));

        assertThat(emails).isEmpty();
    }

    @Test
    @DisplayName("un tenant no puede actualizar filas de otro")
    void aTenantCannotUpdateAnotherTenantsRows() {
        TenantContext.set(tenantA);
        int updated = transactionTemplate.execute(status ->
                jdbcTemplate.update("UPDATE users SET full_name = 'Hackeado' WHERE email = ?",
                        "b@demo.com"));
        TenantContext.clear();

        assertThat(updated).isZero();
        assertThat(readEmailsAs(tenantB)).containsExactly("b@demo.com");
    }

    @Test
    @DisplayName("un tenant no puede borrar filas de otro")
    void aTenantCannotDeleteAnotherTenantsRows() {
        TenantContext.set(tenantA);
        int deleted = transactionTemplate.execute(status ->
                jdbcTemplate.update("DELETE FROM users WHERE email = ?", "b@demo.com"));
        TenantContext.clear();

        assertThat(deleted).isZero();
        assertThat(readEmailsAs(tenantB)).containsExactly("b@demo.com");
    }

    @Test
    @DisplayName("un tenant no puede insertar filas a nombre de otro")
    void aTenantCannotInsertRowsForAnotherTenant() {
        TenantContext.set(tenantA);

        // El seed ya demuestra que el rol puede insertar en su propio tenant.
        // El INSERT cruzado debe fallar por privilegios (42501), independientemente
        // del idioma del servidor PostgreSQL o del wrapper de Spring.
        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status ->
                jdbcTemplate.update("""
                        INSERT INTO users (tenant_id, email, password_hash, full_name, role)
                        VALUES (?, 'intruso@demo.com', 'x', 'Intruso', 'ADMIN')
                        """, tenantB)))
                .rootCause()
                .isInstanceOfSatisfying(SQLException.class,
                        cause -> assertThat(cause.getSQLState()).isEqualTo("42501"));

        TenantContext.clear();
        assertThat(readEmailsAs(tenantB)).containsExactly("b@demo.com");
    }

    @Test
    @DisplayName("sesiones y movimientos de caja quedan aislados y sus llaves impiden referencias cruzadas")
    void cashSessionsAndMovementsAreTenantScoped() {
        assertThat(readCountAs(tenantA, "cash_sessions", sessionA)).isEqualTo(1);
        assertThat(readCountAs(tenantA, "cash_sessions", sessionB)).isZero();
        assertThat(readCountAs(tenantB, "cash_sessions", sessionB)).isEqualTo(1);
        assertThat(readCountAs(tenantB, "cash_sessions", sessionA)).isZero();

        TenantContext.set(tenantA);
        try {
            assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status ->
                    jdbcTemplate.update("""
                            INSERT INTO cash_sessions (tenant_id, cashier_id, opening_fund)
                            VALUES (?, ?, 100)
                            """, tenantA, cashierB)))
                    .rootCause().isInstanceOfSatisfying(SQLException.class,
                            cause -> assertThat(cause.getSQLState()).isEqualTo("23503"));

            assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status ->
                    jdbcTemplate.update("""
                            INSERT INTO cash_movements
                                (tenant_id, cash_session_id, movement_type, amount, reason, recorded_by)
                            VALUES (?, ?, 'DEPOSIT', 10, 'Prueba', ?)
                            """, tenantA, sessionB, cashierA)))
                    .rootCause().isInstanceOfSatisfying(SQLException.class,
                            cause -> assertThat(cause.getSQLState()).isEqualTo("23503"));
        } finally {
            TenantContext.clear();
        }
    }

    private int readCountAs(UUID tenantId, String table, UUID id) {
        TenantContext.set(tenantId);
        try {
            return transactionTemplate.execute(status -> jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM " + table + " WHERE id = ?", Integer.class, id));
        } finally {
            TenantContext.clear();
        }
    }
}
