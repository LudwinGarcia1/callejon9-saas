package com.callejon9.shared.domain;

import com.callejon9.tenancy.TenantContext;
import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * Base de toda entidad gobernada por RLS.
 *
 * El tenant se asigna automaticamente al persistir, de modo que ningun servicio
 * tenga que recordarlo. La politica WITH CHECK de PostgreSQL rechazaria de todos
 * modos un tenant incorrecto: esto es comodidad, no la garantia.
 */
@Getter
@Setter
@MappedSuperclass
public abstract class TenantScopedEntity extends BaseEntity {

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @PrePersist
    void assignTenant() {
        if (this.tenantId == null) {
            this.tenantId = TenantContext.require();
        }
    }
}
