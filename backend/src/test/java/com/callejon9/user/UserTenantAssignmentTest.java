package com.callejon9.user;

import com.callejon9.tenancy.TenantContext;
import com.callejon9.user.domain.User;
import com.callejon9.user.domain.UserRole;
import com.callejon9.user.repository.UserRepository;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cierra un hueco de cobertura de la Task 7: TenantScopedEntity.assignTenant()
 * nunca se ejercito porque ninguna entidad lo extendia todavia. User es la
 * primera, asi que aqui se prueba que el tenant se asigna solo desde
 * TenantContext al persistir, sin que quien llama tenga que pasarlo.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("TenantScopedEntity asigna el tenant automaticamente al persistir")
class UserTenantAssignmentTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private UUID tenantId;

    @AfterEach
    void cleanUp() {
        TenantContext.clear();
        if (tenantId != null) {
            // Sin contexto de tenant, RLS impide borrar el usuario directamente;
            // se borra en cascada eliminando el tenant, que no tiene RLS.
            jdbcTemplate.update("DELETE FROM tenants WHERE id = ?", tenantId);
        }
    }

    @Test
    void tenantIdIsPopulatedFromTenantContextWithoutBeingPassedIn() {
        tenantId = jdbcTemplate.queryForObject(
                "INSERT INTO tenants (name, slug) VALUES ('Tenant Assignment', 'tenant-assignment') RETURNING id",
                UUID.class);

        TenantContext.set(tenantId);
        User saved = transactionTemplate.execute(status -> userRepository.save(User.builder()
                .email("auto-tenant@demo.com")
                .passwordHash("x")
                .fullName("Auto Tenant")
                .role(UserRole.ADMIN)
                .active(true)
                .build()));
        TenantContext.clear();

        assertThat(saved.getTenantId()).isEqualTo(tenantId);
    }
}
