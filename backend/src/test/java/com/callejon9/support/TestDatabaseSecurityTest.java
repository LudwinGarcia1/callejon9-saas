package com.callejon9.support;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class TestDatabaseSecurityTest {
    @Autowired private JdbcTemplate jdbc;

    @Test
    void runtimeRoleCannotBypassRowLevelSecurityOrOwnTables() {
        assertThat(jdbc.queryForObject("SELECT current_user", String.class)).isEqualTo("callejon9_app");
        assertThat(jdbc.queryForObject("""
                SELECT rolsuper OR rolbypassrls OR rolcreaterole OR rolcreatedb
                FROM pg_roles WHERE rolname = current_user
                """, Boolean.class)).isFalse();
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM pg_tables
                WHERE schemaname = 'public' AND tableowner = current_user
                """, Integer.class)).isZero();
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
                WHERE n.nspname = 'public' AND c.relkind = 'r'
                  AND c.relrowsecurity AND c.relforcerowsecurity
                """, Integer.class)).isEqualTo(14);
    }
}
