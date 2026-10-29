package com.callejon9.platform.tenant.domain;

import com.callejon9.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "tenants")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Tenant extends BaseEntity {

    public static final String DEFAULT_TIMEZONE = "America/Mexico_City";

    @Column(nullable = false, length = 160)
    private String name;

    @Column(nullable = false, unique = true, length = 80)
    private String slug;

    @Column(nullable = false)
    private boolean active;

    /**
     * ID IANA de la zona en la que el restaurante corta sus dias de venta
     * (ver {@link com.callejon9.shared.time.BusinessCalendar}). Debe coincidir
     * con el default de la columna en V15.
     */
    @Column(nullable = false, length = 64)
    @Builder.Default
    private String timezone = DEFAULT_TIMEZONE;
}
