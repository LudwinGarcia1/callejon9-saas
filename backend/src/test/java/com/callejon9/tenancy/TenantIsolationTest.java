package com.callejon9.tenancy;

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

    @BeforeEach
    void seedTenants() {
        // La tabla tenants es control plane: no tiene RLS, se puede escribir sin contexto.
        tenantA = jdbcTemplate.queryForObject(
                "INSERT INTO tenants (name, slug) VALUES ('Tenant A', 'tenant-a') RETURNING id",
                UUID.class);
        tenantB = jdbcTemplate.queryForObject(
                "INSERT INTO tenants (name, slug) VALUES ('Tenant B', 'tenant-b') RETURNING id",
                UUID.class);

        insertUserAs(tenantA, "a@demo.com", "Usuario A");
        insertUserAs(tenantB, "b@demo.com", "Usuario B");
    }

    @AfterEach
    void cleanUp() {
        TenantContext.clear();
        // Sin contexto de tenant, RLS impide borrar users; se borra en cascada
        // eliminando los tenants, que no estan protegidos por RLS.
        jdbcTemplate.update("DELETE FROM tenants WHERE slug IN ('tenant-a','tenant-b')");
    }

    private void insertUserAs(UUID tenantId, String email, String name) {
        TenantContext.set(tenantId);
        transactionTemplate.executeWithoutResult(status ->
                jdbcTemplate.update("""
                        INSERT INTO users (tenant_id, email, password_hash, full_name, role)
                        VALUES (?, ?, 'x', ?, 'ADMIN')
                        """, tenantId, email, name));
        TenantContext.clear();
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

        // Spring Framework 6 ya no propaga el mensaje de la causa dentro de
        // getMessage() del wrapper (NestedRuntimeException dejo de anexar
        // "nested exception is ..."). El mensaje de Postgres si aparece en el
        // stack trace completo, vía la cadena "Caused by".
        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status ->
                jdbcTemplate.update("""
                        INSERT INTO users (tenant_id, email, password_hash, full_name, role)
                        VALUES (?, 'intruso@demo.com', 'x', 'Intruso', 'ADMIN')
                        """, tenantB)))
                .hasStackTraceContaining("row-level security");

        TenantContext.clear();
        assertThat(readEmailsAs(tenantB)).containsExactly("b@demo.com");
    }
}
