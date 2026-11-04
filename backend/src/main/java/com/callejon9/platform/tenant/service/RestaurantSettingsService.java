package com.callejon9.platform.tenant.service;

import com.callejon9.platform.tenant.domain.Tenant;
import com.callejon9.platform.tenant.repository.TenantRepository;
import com.callejon9.shared.error.ResourceNotFoundException;
import com.callejon9.tenancy.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Configuracion del restaurante activo. El tenant sale siempre de
 * {@link TenantContext} -- es decir, de la cookie firmada --, nunca de la
 * peticion: no hay forma de nombrar la configuracion de otro restaurante.
 * {@code tenants} no tiene RLS, asi que esa es la unica barrera y no se
 * acepta un id en la ruta ni en el cuerpo.
 */
@Service
public class RestaurantSettingsService {

    private final TenantRepository tenantRepository;

    public RestaurantSettingsService(TenantRepository tenantRepository) {
        this.tenantRepository = tenantRepository;
    }

    @Transactional(readOnly = true)
    public Tenant current() {
        return tenantRepository.findById(TenantContext.require())
                .orElseThrow(() -> new ResourceNotFoundException("El restaurante no existe."));
    }

    /**
     * Cambia la zona en la que se cortan los dias. Los instantes guardados no
     * se tocan: siguen en UTC y el historial se reinterpreta con la zona
     * nueva en la siguiente consulta.
     */
    @Transactional
    public Tenant updateTimezone(String timezone) {
        Tenant tenant = tenantRepository.findByIdForUpdate(TenantContext.require())
                .orElseThrow(() -> new ResourceNotFoundException("El restaurante no existe."));
        tenant.setTimezone(timezone);
        return tenantRepository.saveAndFlush(tenant);
    }
}
