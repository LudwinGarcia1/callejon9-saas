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
 * Sin tenant no se fija la variable: RLS entonces no revela ninguna fila.
 * Fallar cerrado es deliberado. Esto es seguro incluso cuando una conexion
 * pooled ya tuvo un tenant real fijado antes (y por lo tanto
 * current_setting('app.tenant_id', true) devuelve '' en vez de NULL, un
 * detalle de los GUC personalizados de Postgres) porque la politica RLS
 * (V5__rls_policy_null_safe.sql) envuelve la lectura en
 * nullif(current_setting(...), '')::uuid, convirtiendo esa cadena vacia en
 * NULL antes del cast. La garantia de aislamiento vive en el motor, no aqui:
 * cualquier otro camino que llegue a estas tablas sin pasar por este
 * TransactionManager (otro bean transaccional, un job con JdbcTemplate
 * crudo, una conexion tomada directo del pool) queda igual de cerrado por
 * la politica, sin depender de que Java recuerde fijar nada.
 */
public class TenantAwareTransactionManager extends JpaTransactionManager {

    public TenantAwareTransactionManager(EntityManagerFactory entityManagerFactory) {
        super(entityManagerFactory);
    }

    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {
        super.doBegin(transaction, definition);

        UUID tenantId = TenantContext.currentOrNull();
        if (tenantId == null) {
            // Sin tenant no se fija la variable: RLS entonces no revela ninguna
            // fila. Fallar cerrado es deliberado.
            return;
        }

        EntityManagerFactory emf = Objects.requireNonNull(getEntityManagerFactory());
        EntityManagerHolder holder =
                (EntityManagerHolder) TransactionSynchronizationManager.getResource(emf);
        EntityManager entityManager = Objects.requireNonNull(holder).getEntityManager();

        entityManager
                .createNativeQuery("SELECT set_config('app.tenant_id', :tenantId, true)")
                .setParameter("tenantId", tenantId.toString())
                .getSingleResult();
    }
}
