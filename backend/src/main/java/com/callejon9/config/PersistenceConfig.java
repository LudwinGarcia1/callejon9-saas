package com.callejon9.config;

import com.callejon9.tenancy.TenantAwareTransactionManager;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class PersistenceConfig {

    /**
     * Reemplaza el JpaTransactionManager por defecto para que TODA transaccion
     * fije app.tenant_id. Si esta sustitucion no ocurre, RLS oculta todo y la
     * aplicacion deja de funcionar de forma evidente: el fallo es ruidoso, no
     * silencioso.
     */
    @Bean
    @Primary
    public PlatformTransactionManager transactionManager(EntityManagerFactory emf) {
        return new TenantAwareTransactionManager(emf);
    }
}
