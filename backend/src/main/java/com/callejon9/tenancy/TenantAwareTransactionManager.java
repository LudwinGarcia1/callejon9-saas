package com.callejon9.tenancy;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.util.Objects;
import java.util.UUID;
import org.springframework.orm.jpa.EntityManagerHolder;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Publica el tenant activo a la sesion de PostgreSQL al abrir cada transaccion.
 *
 * El tercer argumento de set_config es {@code true}, que hace la variable LOCAL
 * A LA TRANSACCION. Esto es lo que impide que el tenant se filtre a otra
 * peticion cuando HikariCP devuelve la conexion al pool.
 *
 * IMPORTANTE: la variable se fija en TODAS las transacciones, incluso sin
 * tenant activo. Postgres crea "app.tenant_id" como un GUC de tipo placeholder
 * la primera vez que se usa en una conexion; a partir de ese momento,
 * current_setting('app.tenant_id', true) ya no vuelve a devolver NULL cuando
 * la variable esta "sin fijar" dentro de esa misma conexion fisica, sino
 * cadena vacia (''), y ''::uuid lanza una excepcion en vez de simplemente no
 * revelar filas. Con un pool de conexiones (HikariCP) esto es inevitable en
 * cuanto una transaccion cualquiera fijo el tenant en esa conexion. Por eso,
 * cuando no hay tenant activo se fija el UUID nulo (todo ceros), que nunca
 * coincide con un tenant_id real (tenants.id usa gen_random_uuid()): RLS sigue
 * ocultando todas las filas, pero de forma silenciosa en vez de lanzar error.
 */
public class TenantAwareTransactionManager extends JpaTransactionManager {

    private static final UUID NO_TENANT = new UUID(0L, 0L);

    public TenantAwareTransactionManager(EntityManagerFactory entityManagerFactory) {
        super(entityManagerFactory);
    }

    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {
        super.doBegin(transaction, definition);

        UUID tenantId = TenantContext.currentOrNull();
        String tenantValue = (tenantId != null ? tenantId : NO_TENANT).toString();

        EntityManagerFactory emf = Objects.requireNonNull(getEntityManagerFactory());
        EntityManagerHolder holder =
                (EntityManagerHolder) TransactionSynchronizationManager.getResource(emf);
        EntityManager entityManager = Objects.requireNonNull(holder).getEntityManager();

        entityManager
                .createNativeQuery("SELECT set_config('app.tenant_id', :tenantId, true)")
                .setParameter("tenantId", tenantValue)
                .getSingleResult();
    }
}
